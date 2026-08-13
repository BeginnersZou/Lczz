package com.lczz.dashboard.service;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("MM-dd");
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy-MM");
    private static final Map<String, StatusDefinition> STATUS_DEFINITIONS = new LinkedHashMap<>();

    static {
        STATUS_DEFINITIONS.put("PENDING_VISIT", new StatusDefinition("待上门", "#3b82f6"));
        STATUS_DEFINITIONS.put("IN_PROGRESS", new StatusDefinition("进行中", "#84cc16"));
        STATUS_DEFINITIONS.put("PENDING_REVIEW", new StatusDefinition("待评价", "#f97316"));
        STATUS_DEFINITIONS.put("REVIEWED", new StatusDefinition("已评价", "#06b6d4"));
        STATUS_DEFINITIONS.put("CANCELLED", new StatusDefinition("已取消", "#64748b"));
    }

    private final JdbcTemplate jdbcTemplate;

    public DashboardService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public Overview overview(String range) {
        RangeWindow window = RangeWindow.parse(range);
        long rangeOrders = countOrdersCreatedSince(window.start());
        Map<String, Long> statusCounts = orderStatusCounts(null, null);
        long reviewCount = count("SELECT COUNT(*) FROM work_order_review");
        BigDecimal rating = jdbcTemplate.queryForObject(
                "SELECT COALESCE(AVG(score), 0) FROM work_order_review WHERE score IS NOT NULL", BigDecimal.class);
        List<RatingBar> ratingBars = ratingBars(reviewCount);
        return new Overview(
                rangeOrders,
                statusCounts.getOrDefault("PENDING_VISIT", 0L),
                statusCounts.getOrDefault("IN_PROGRESS", 0L),
                statusCounts.getOrDefault("PENDING_REVIEW", 0L) + statusCounts.getOrDefault("REVIEWED", 0L),
                count("SELECT COUNT(*) FROM product WHERE deleted = FALSE AND enabled = TRUE "
                        + "AND display_stock IS NOT NULL AND display_stock <= ?", LOW_STOCK_THRESHOLD),
                count("SELECT COUNT(*) FROM sys_user WHERE deleted = FALSE AND audit_status = 'PENDING'"),
                rating == null ? BigDecimal.ZERO : rating.setScale(1, RoundingMode.HALF_UP),
                reviewCount,
                ratingBars);
    }

    @Transactional(readOnly = true)
    public Trend orderTrend(String range) {
        RangeWindow window = RangeWindow.parse(range);
        Map<String, Long> buckets = window.emptyBuckets();
        List<LocalDateTime> createdTimes = jdbcTemplate.query(
                "SELECT created_at FROM work_order WHERE deleted = FALSE AND created_at >= ? ORDER BY created_at",
                (resultSet, rowNum) -> resultSet.getTimestamp("created_at").toLocalDateTime(),
                Timestamp.valueOf(window.start()));
        for (LocalDateTime createdAt : createdTimes) {
            String key = window.monthly() ? YearMonth.from(createdAt).format(MONTH_LABEL)
                    : createdAt.toLocalDate().format(DAY_LABEL);
            buckets.computeIfPresent(key, (ignored, value) -> value + 1);
        }
        List<Long> values = new ArrayList<>(buckets.values());
        return new Trend(new ArrayList<>(buckets.keySet()), List.of(new TrendSeries("订单数", values)), values);
    }

    @Transactional(readOnly = true)
    public List<StatusMetric> orderStatus() {
        Map<String, Long> counts = orderStatusCounts(null, null);
        return STATUS_DEFINITIONS.entrySet().stream().map(entry -> new StatusMetric(
                entry.getValue().name(), counts.getOrDefault(entry.getKey(), 0L), entry.getValue().color())).toList();
    }

    @Transactional(readOnly = true)
    public TodoPage todo(AuthenticatedUser actor, int page, int pageSize) {
        TodoScope scope = TodoScope.forActor(actor);
        Map<String, Long> allStatusCounts = orderStatusCounts(scope.column(), scope.userId());
        List<String> todoStatuses = scope.todoStatuses();
        String placeholders = String.join(",", todoStatuses.stream().map(ignored -> "?").toList());
        StringBuilder where = new StringBuilder(" WHERE deleted = FALSE AND order_status IN (")
                .append(placeholders).append(')');
        List<Object> args = new ArrayList<>(todoStatuses);
        if (scope.column() != null) {
            where.append(" AND ").append(scope.column()).append(" = ?");
            args.add(scope.userId());
        }
        long total = count("SELECT COUNT(*) FROM work_order" + where, args.toArray());
        args.add(pageSize);
        args.add((page - 1) * pageSize);
        List<TodoItem> items = jdbcTemplate.query(
                "SELECT id, order_no, task_type, order_status, customer_name, required_start_at "
                        + "FROM work_order" + where + " ORDER BY created_at DESC LIMIT ? OFFSET ?",
                (resultSet, rowNum) -> new TodoItem(
                        resultSet.getLong("id"), resultSet.getString("order_no"),
                        resultSet.getString("task_type"), resultSet.getString("order_status"),
                        resultSet.getString("customer_name"), nullableDateTime(resultSet.getTimestamp("required_start_at"))),
                args.toArray());
        return new TodoPage(items, total, page, pageSize, scope.role().name(),
                allStatusCounts.getOrDefault("PENDING_VISIT", 0L),
                allStatusCounts.getOrDefault("IN_PROGRESS", 0L),
                allStatusCounts.getOrDefault("PENDING_REVIEW", 0L) + allStatusCounts.getOrDefault("REVIEWED", 0L));
    }

    private long countOrdersCreatedSince(LocalDateTime start) {
        return count("SELECT COUNT(*) FROM work_order WHERE deleted = FALSE AND created_at >= ?", Timestamp.valueOf(start));
    }

    private Map<String, Long> orderStatusCounts(String scopeColumn, Long userId) {
        String sql = "SELECT order_status, COUNT(*) AS total FROM work_order WHERE deleted = FALSE";
        Object[] args = new Object[0];
        if (scopeColumn != null) {
            sql += " AND " + scopeColumn + " = ?";
            args = new Object[]{userId};
        }
        sql += " GROUP BY order_status";
        Map<String, Long> result = new LinkedHashMap<>();
        jdbcTemplate.query(sql, (RowCallbackHandler) row ->
                result.put(row.getString("order_status"), row.getLong("total")), args);
        return result;
    }

    private List<RatingBar> ratingBars(long reviewCount) {
        Map<Integer, Long> scores = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT score, COUNT(*) AS total FROM work_order_review WHERE score IS NOT NULL GROUP BY score",
                (RowCallbackHandler) row -> scores.put(row.getInt("score"), row.getLong("total")));
        List<RatingBar> result = new ArrayList<>();
        for (int star = 5; star >= 1; star--) {
            long count = scores.getOrDefault(star, 0L);
            int percent = reviewCount == 0 ? 0 : (int) Math.round(count * 100.0 / reviewCount);
            result.add(new RatingBar(star, percent, count));
        }
        return result;
    }

    private long count(String sql, Object... args) {
        Long result = jdbcTemplate.queryForObject(sql, Long.class, args);
        return result == null ? 0 : result;
    }

    private static LocalDateTime nullableDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }

    public record Overview(long todayOrders, long pendingAssign, long processingOrders, long completedOrders,
                           long lowStock, long pendingAudit, BigDecimal rating, long ratingCount,
                           List<RatingBar> ratingBars) { }
    public record RatingBar(int star, int percent, long count) { }
    public record Trend(List<String> xAxis, List<TrendSeries> series, List<Long> values) { }
    public record TrendSeries(String name, List<Long> data) { }
    public record StatusMetric(String name, long value, String color) { }
    public record TodoPage(List<TodoItem> list, long total, int page, int pageSize, String role,
                           long pending, long processing, long done) { }
    public record TodoItem(long id, String orderNo, String taskType, String status, String customerName,
                           LocalDateTime requiredStartAt) { }

    private record StatusDefinition(String name, String color) { }

    private record TodoScope(RoleCode role, String column, Long userId, List<String> todoStatuses) {
        private static TodoScope forActor(AuthenticatedUser actor) {
            if (actor.hasRole(RoleCode.ADMIN)) {
                return new TodoScope(RoleCode.ADMIN, null, null,
                        List.of("PENDING_VISIT", "IN_PROGRESS", "PENDING_REVIEW"));
            }
            if (actor.hasRole(RoleCode.INSTALLER)) {
                return new TodoScope(RoleCode.INSTALLER, "installer_user_id", actor.userId(),
                        List.of("PENDING_VISIT", "IN_PROGRESS"));
            }
            if (actor.hasRole(RoleCode.CUSTOMER) || actor.hasRole(RoleCode.DEALER)) {
                RoleCode role = actor.hasRole(RoleCode.DEALER) ? RoleCode.DEALER : RoleCode.CUSTOMER;
                return new TodoScope(role, "customer_user_id", actor.userId(), List.of("PENDING_REVIEW"));
            }
            throw new BusinessException(403, "DASHBOARD_FORBIDDEN", "当前角色无权访问工作台");
        }
    }

    private record RangeWindow(LocalDateTime start, boolean monthly, int bucketCount) {
        private static RangeWindow parse(String value) {
            String normalized = value == null || value.isBlank() ? "7d" : value.trim().toLowerCase();
            LocalDate today = LocalDate.now();
            return switch (normalized) {
                case "day" -> new RangeWindow(today.atStartOfDay(), false, 1);
                case "7d" -> new RangeWindow(today.minusDays(6).atStartOfDay(), false, 7);
                case "30d" -> new RangeWindow(today.minusDays(29).atStartOfDay(), false, 30);
                case "summary" -> new RangeWindow(YearMonth.from(today).minusMonths(11).atDay(1).atStartOfDay(), true, 12);
                default -> throw new BusinessException("INVALID_DASHBOARD_RANGE",
                        "range 仅支持 day、7d、30d 或 summary");
            };
        }

        private Map<String, Long> emptyBuckets() {
            Map<String, Long> result = new LinkedHashMap<>();
            if (monthly) {
                YearMonth first = YearMonth.from(start);
                for (int index = 0; index < bucketCount; index++) {
                    result.put(first.plusMonths(index).format(MONTH_LABEL), 0L);
                }
            } else {
                LocalDate first = start.toLocalDate();
                for (int index = 0; index < bucketCount; index++) {
                    result.put(first.plusDays(index).format(DAY_LABEL), 0L);
                }
            }
            return result;
        }
    }
}
