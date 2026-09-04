package com.lczz.stocking.service;

import com.lczz.common.exception.BusinessException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-only projection that combines engineering material requests (W) and installer self orders (A).
 * The two domains deliberately keep separate primary keys and tables so a self order can never be
 * mistaken for, or attached to, a work order.
 */
@Service
public class UnifiedPreparationService {
    private static final int EXPORT_LIMIT = 10_000;
    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final JdbcTemplate jdbc;

    public UnifiedPreparationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PreparationPage list(int page, int pageSize, String keyword, String status, String source) {
        Query query = query(keyword, status, source);
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + query.sql() + ") unified_count",
                Long.class, query.args().toArray());
        List<Object> pageArgs = new ArrayList<>(query.args());
        pageArgs.add(pageSize);
        pageArgs.add((page - 1) * pageSize);
        List<PreparationSummary> rows = jdbc.query(query.sql()
                        + " ORDER BY created_at DESC, source_type, source_id DESC LIMIT ? OFFSET ?",
                this::summary, pageArgs.toArray());
        return new PreparationPage(rows, total, page, pageSize);
    }

    public PreparationView detail(String source, long id) {
        String normalized = normalizeSource(source, false);
        return "A".equals(normalized) ? selfOrderDetail(id) : workRequestDetail(id);
    }

    public ExportFile exportList(String keyword, String status, String source) {
        Query query = query(keyword, status, source);
        String exportSql = """
                SELECT b.order_no,b.order_name,b.source_label,b.customer_name,b.submitter_name,b.source_type,
                       b.status_code,b.created_at,
                       CASE WHEN b.source_type='W' THEN wi.product_name_snapshot ELSE ai.product_name_snapshot END material_name,
                       CASE WHEN b.source_type='W' THEN COALESCE(wi.sku_code_snapshot,wi.product_code_snapshot) ELSE ai.sku_code_snapshot END sku_code,
                       CASE WHEN b.source_type='W' THEN COALESCE(NULLIF(wi.sku_spec_snapshot,''),wi.model_spec_snapshot) ELSE ai.spec_snapshot END spec,
                       CASE WHEN b.source_type='W' THEN wi.unit_snapshot ELSE ai.unit_snapshot END unit_name,
                       CASE WHEN b.source_type='W' THEN wi.requested_quantity ELSE ai.quantity END quantity,
                       CASE WHEN b.source_type='W' THEN wi.id ELSE ai.id END item_id
                FROM (
                """ + query.sql() + """
                ) b
                LEFT JOIN material_request_item wi ON b.source_type='W' AND wi.request_id=b.source_id
                LEFT JOIN material_self_order_item ai ON b.source_type='A' AND ai.self_order_id=b.source_id
                """;
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + exportSql + ") unified_export_count",
                Long.class, query.args().toArray());
        if (total > EXPORT_LIMIT) {
            throw new BusinessException(413, "PREPARATION_EXPORT_TOO_LARGE",
                    "当前筛选结果超过10000条明细，请缩小筛选范围后再导出");
        }
        List<List<String>> rows = jdbc.query(exportSql + " ORDER BY b.created_at DESC,b.source_type,b.source_id DESC,item_id",
                (rs, row) -> List.of(rs.getString("order_no"), rs.getString("order_name"),
                        rs.getString("source_label"), nullToEmpty(rs.getString("customer_name")),
                        rs.getString("submitter_name"), nullToEmpty(rs.getString("material_name")),
                        nullToEmpty(rs.getString("sku_code")), nullToEmpty(rs.getString("spec")),
                        nullToEmpty(rs.getString("unit_name")), plain(rs.getBigDecimal("quantity")),
                        rs.getTimestamp("created_at").toLocalDateTime().format(CSV_TIME),
                        statusLabel(rs.getString("source_type"), rs.getString("status_code"))),
                query.args().toArray());
        return csv("备货总表_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv",
                List.of("订单编号", "订单名称", "来源", "客户", "下单人/师傅", "耗材名称", "SKU编码", "规格",
                        "单位", "数量", "创建时间", "状态"), rows);
    }

    public ExportFile exportDetail(String source, long id) {
        PreparationView view = detail(source, id);
        List<List<String>> rows = view.materials().isEmpty()
                ? List.of(exportRow(view, null))
                : view.materials().stream().map(item -> exportRow(view, item)).toList();
        return csv("备货明细_" + safeFilename(view.orderNo()) + ".csv",
                List.of("订单编号", "订单名称", "来源", "客户", "下单人/师傅", "耗材名称", "SKU编码", "规格",
                        "单位", "数量", "创建时间", "状态"), rows);
    }

    private Query query(String keyword, String status, String source) {
        String normalizedSource = normalizeSource(source, true);
        String normalizedStatus = normalizeStatus(status);
        String word = keyword == null ? "" : keyword.trim();
        List<String> selects = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (normalizedSource == null || "W".equals(normalizedSource)) {
            StringBuilder sql = new StringBuilder("""
                    SELECT 'W' source_type,r.id source_id,r.request_no request_no,w.order_no order_no,
                           COALESCE(NULLIF(w.description,''),NULLIF(w.task_type,''),'安装订单') order_name,
                           '工程订单耗材' source_label,w.customer_name customer_name,
                           COALESCE(NULLIF(u.real_name,''),NULLIF(u.nickname,''),NULLIF(u.username,''),'安装师傅') submitter_name,
                           r.installer_user_id submitter_id,r.request_status status_code,r.submitted_at created_at,
                           r.order_id work_order_id,
                           (SELECT COUNT(*) FROM material_request_item x WHERE x.request_id=r.id) item_count,
                           (SELECT COALESCE(SUM(x.requested_quantity),0) FROM material_request_item x WHERE x.request_id=r.id) total_quantity
                    FROM material_request r JOIN work_order w ON w.id=r.order_id
                    LEFT JOIN sys_user u ON u.id=r.installer_user_id
                    WHERE w.deleted=FALSE
                    """);
            if (normalizedStatus != null) {
                if (normalizedStatus.startsWith("A:")) sql.append(" AND 1=0");
                else { sql.append(" AND r.request_status=?"); args.add(normalizedStatus); }
            }
            if (!word.isEmpty()) {
                String like = "%" + word + "%";
                sql.append("""
                         AND (w.order_no LIKE ? OR r.request_no LIKE ? OR w.description LIKE ? OR w.task_type LIKE ?
                              OR w.customer_name LIKE ? OR u.real_name LIKE ? OR u.nickname LIKE ? OR u.username LIKE ?
                              OR EXISTS (SELECT 1 FROM material_request_item x WHERE x.request_id=r.id
                                         AND (x.product_name_snapshot LIKE ? OR x.product_code_snapshot LIKE ?
                                              OR x.sku_code_snapshot LIKE ? OR x.sku_spec_snapshot LIKE ?
                                              OR x.model_spec_snapshot LIKE ?)))
                        """);
                for (int i = 0; i < 13; i++) args.add(like);
            }
            selects.add(sql.toString());
        }
        if (normalizedSource == null || "A".equals(normalizedSource)) {
            StringBuilder sql = new StringBuilder("""
                    SELECT 'A' source_type,o.id source_id,o.order_no request_no,o.order_no order_no,
                           '客户下单' order_name,'师傅自助下单' source_label,NULL customer_name,
                           COALESCE(NULLIF(u.real_name,''),NULLIF(u.nickname,''),NULLIF(u.username,''),'安装师傅') submitter_name,
                           o.installer_id submitter_id,o.order_status status_code,o.created_at created_at,
                           NULL work_order_id,
                           (SELECT COUNT(*) FROM material_self_order_item x WHERE x.self_order_id=o.id) item_count,
                           (SELECT COALESCE(SUM(x.quantity),0) FROM material_self_order_item x WHERE x.self_order_id=o.id) total_quantity
                    FROM material_self_order o LEFT JOIN sys_user u ON u.id=o.installer_id WHERE 1=1
                    """);
            if (normalizedStatus != null) {
                if (normalizedStatus.startsWith("A:")) { sql.append(" AND o.order_status=?"); args.add(normalizedStatus.substring(2)); }
                else sql.append(" AND 1=0");
            }
            if (!word.isEmpty()) {
                String like = "%" + word + "%";
                sql.append("""
                         AND (o.order_no LIKE ? OR o.order_name LIKE ? OR u.real_name LIKE ? OR u.nickname LIKE ?
                              OR u.username LIKE ? OR EXISTS (SELECT 1 FROM material_self_order_item x
                                  WHERE x.self_order_id=o.id AND (x.product_name_snapshot LIKE ?
                                  OR x.sku_code_snapshot LIKE ? OR x.spec_snapshot LIKE ?)))
                        """);
                for (int i = 0; i < 8; i++) args.add(like);
            }
            selects.add(sql.toString());
        }
        if (selects.isEmpty()) throw new BusinessException("INVALID_PREPARATION_SOURCE", "备货来源不合法");
        return new Query(String.join(" UNION ALL ", selects), args);
    }

    private PreparationView workRequestDetail(long id) {
        List<PreparationView> rows = jdbc.query("""
                SELECT r.id,r.request_no,w.order_no,
                       COALESCE(NULLIF(w.description,''),NULLIF(w.task_type,''),'安装订单') order_name,
                       w.customer_name,COALESCE(NULLIF(u.real_name,''),NULLIF(u.nickname,''),NULLIF(u.username,''),'安装师傅') submitter_name,
                       r.installer_user_id,r.request_status,r.submitted_at,r.order_id,r.remark
                FROM material_request r JOIN work_order w ON w.id=r.order_id
                LEFT JOIN sys_user u ON u.id=r.installer_user_id WHERE r.id=? AND w.deleted=FALSE
                """, (rs, row) -> new PreparationView(rs.getLong("id"), "W", rs.getString("request_no"),
                rs.getString("order_no"), rs.getString("order_name"), "工程订单耗材", rs.getString("customer_name"),
                rs.getLong("installer_user_id"), rs.getString("submitter_name"), rs.getString("request_status"),
                statusLabel("W", rs.getString("request_status")), rs.getTimestamp("submitted_at").toLocalDateTime(),
                rs.getObject("order_id", Long.class), rs.getString("remark"), new ArrayList<>()), id);
        if (rows.isEmpty()) throw new BusinessException(404, "PREPARATION_NOT_FOUND", "备货记录不存在");
        PreparationView view = rows.getFirst();
        view.materials().addAll(jdbc.query("""
                SELECT i.id,i.product_id,i.sku_id,COALESCE(i.sku_code_snapshot,i.product_code_snapshot) sku_code,
                       i.product_name_snapshot,COALESCE(NULLIF(i.sku_spec_snapshot,''),i.model_spec_snapshot) sku_spec,
                       i.unit_snapshot,i.requested_quantity,i.prepared_quantity,i.item_status,
                       COALESCE(s.stock,p.display_stock) current_stock
                FROM material_request_item i LEFT JOIN product_sku s ON s.id=i.sku_id
                LEFT JOIN product p ON p.id=i.product_id
                WHERE i.request_id=? ORDER BY i.id
                """, (rs, row) -> new MaterialLine(rs.getLong("id"), rs.getObject("product_id", Long.class),
                rs.getObject("sku_id", Long.class), rs.getString("sku_code"), rs.getString("product_name_snapshot"),
                rs.getString("sku_spec"), rs.getString("unit_snapshot"),
                rs.getBigDecimal("requested_quantity"), rs.getBigDecimal("prepared_quantity"),
                "PREPARED".equals(rs.getString("item_status")), rs.getString("item_status"),
                rs.getBigDecimal("current_stock")), id));
        return view;
    }

    private PreparationView selfOrderDetail(long id) {
        List<PreparationView> rows = jdbc.query("""
                SELECT o.id,o.order_no,o.order_status,o.created_at,o.installer_id,
                       COALESCE(NULLIF(u.real_name,''),NULLIF(u.nickname,''),NULLIF(u.username,''),'安装师傅') submitter_name
                FROM material_self_order o LEFT JOIN sys_user u ON u.id=o.installer_id WHERE o.id=?
                """, (rs, row) -> new PreparationView(rs.getLong("id"), "A", rs.getString("order_no"),
                rs.getString("order_no"), "客户下单", "师傅自助下单", null,
                rs.getLong("installer_id"), rs.getString("submitter_name"), rs.getString("order_status"),
                statusLabel("A", rs.getString("order_status")), rs.getTimestamp("created_at").toLocalDateTime(),
                null, null, new ArrayList<>()), id);
        if (rows.isEmpty()) throw new BusinessException(404, "PREPARATION_NOT_FOUND", "备货记录不存在");
        PreparationView view = rows.getFirst();
        view.materials().addAll(jdbc.query("""
                SELECT id,product_id,sku_id,sku_code_snapshot,product_name_snapshot,spec_snapshot,
                       unit_snapshot,quantity FROM material_self_order_item WHERE self_order_id=? ORDER BY id
                """, (rs, row) -> new MaterialLine(rs.getLong("id"), rs.getObject("product_id", Long.class),
                rs.getObject("sku_id", Long.class), rs.getString("sku_code_snapshot"),
                rs.getString("product_name_snapshot"), rs.getString("spec_snapshot"),
                rs.getString("unit_snapshot"), rs.getBigDecimal("quantity"), BigDecimal.ZERO,
                false, "ORDERED", null), id));
        return view;
    }

    private PreparationSummary summary(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        String source = rs.getString("source_type");
        String code = rs.getString("status_code");
        return new PreparationSummary(rs.getLong("source_id"), source, rs.getString("request_no"),
                rs.getString("order_no"), rs.getString("order_name"), rs.getString("source_label"),
                rs.getString("customer_name"), rs.getLong("submitter_id"), rs.getString("submitter_name"),
                code, statusLabel(source, code), rs.getTimestamp("created_at").toLocalDateTime(),
                rs.getObject("work_order_id", Long.class), rs.getInt("item_count"),
                rs.getBigDecimal("total_quantity"));
    }

    private List<String> exportRow(PreparationView view, MaterialLine item) {
        return List.of(view.orderNo(), view.productName(), view.sourceLabel(), nullToEmpty(view.customerName()),
                view.submitterName(), item == null ? "" : item.name(), item == null ? "" : item.skuCode(),
                item == null ? "" : nullToEmpty(item.spec()), item == null ? "" : item.unit(),
                item == null ? "" : plain(item.quantity()), view.createTime().format(CSV_TIME), view.statusLabel());
    }

    private ExportFile csv(String filename, List<String> headers, List<List<String>> rows) {
        StringBuilder data = new StringBuilder("\uFEFF");
        data.append(headers.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        for (List<String> row : rows) {
            data.append(row.stream().map(this::csvCell).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
        }
        return new ExportFile(filename, data.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String csvCell(String value) {
        String safe = value == null ? "" : value;
        if (!safe.isEmpty() && "=+-@".indexOf(safe.charAt(0)) >= 0) safe = "'" + safe;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String normalizeSource(String source, boolean nullable) {
        if (source == null || source.isBlank()) return nullable ? null : "W";
        String value = source.trim().toUpperCase(Locale.ROOT);
        if (!"W".equals(value) && !"A".equals(value))
            throw new BusinessException("INVALID_PREPARATION_SOURCE", "备货来源不合法");
        return value;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String raw = status.trim();
        String upper = raw.toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "待备货" -> "PENDING";
            case "备货中" -> "PREPARING";
            case "已备货" -> "DONE";
            case "已作废" -> "VOIDED";
            case "已下单" -> "A:ORDERED";
            default -> switch (upper) {
                case "PENDING", "PREPARING", "DONE", "VOIDED" -> upper;
                case "ORDERED" -> "A:ORDERED";
                default -> throw new BusinessException("INVALID_PREPARATION_STATUS", "备货状态不合法");
            };
        };
    }

    private String statusLabel(String source, String code) {
        if ("A".equals(source)) return "ORDERED".equals(code) ? "已下单" : code;
        return switch (code) {
            case "PENDING" -> "待备货";
            case "PREPARING" -> "备货中";
            case "DONE" -> "已备货";
            case "VOIDED" -> "已作废";
            default -> code;
        };
    }

    private static String plain(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private static String safeFilename(String value) {
        return (value == null ? "备货记录" : value).replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_");
    }

    private record Query(String sql, List<Object> args) { }
    public record PreparationPage(List<PreparationSummary> list, long total, int page, int pageSize) { }
    public record PreparationSummary(long id, String source, String requestNo, String orderNo, String productName,
                                     String sourceLabel, String customerName, long submitterId,
                                     String submitterName, String statusCode, String statusLabel,
                                     LocalDateTime createTime, Long orderId, int itemCount,
                                     BigDecimal totalQuantity) { }
    public record PreparationView(long id, String source, String requestNo, String orderNo, String productName,
                                  String sourceLabel, String customerName, long submitterId,
                                  String submitterName, String statusCode, String statusLabel,
                                  LocalDateTime createTime, Long orderId, String remark,
                                  List<MaterialLine> materials) { }
    public record MaterialLine(long id, Long productId, Long skuId, String skuCode, String name, String spec,
                               String unit, BigDecimal count, BigDecimal preparedQuantity,
                               boolean checked, String itemStatus, BigDecimal stock) {
        public BigDecimal quantity() { return count; }
    }
    public record ExportFile(String filename, byte[] content) { }
}
