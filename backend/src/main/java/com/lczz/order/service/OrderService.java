package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.persistence.WorkOrderAssignmentEntity;
import com.lczz.order.persistence.WorkOrderAssignmentMapper;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.persistence.WorkOrderStatusHistoryEntity;
import com.lczz.order.persistence.WorkOrderStatusHistoryMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Map<String, String> TASK_LABELS = Map.of(
            "AIR_CONDITIONING_INSTALL", "空调安装",
            "AIR_CONDITIONING_REPAIR", "空调维修",
            "AIR_CONDITIONING_CLEAN", "空调清洗",
            "AIR_CONDITIONING_RELOCATE", "空调移机");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PENDING_VISIT", "待上门",
            "IN_PROGRESS", "处理中",
            "PENDING_REVIEW", "已完成",
            "REVIEWED", "已完成",
            "CANCELLED", "已作废");
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "PENDING_VISIT", Set.of("IN_PROGRESS", "CANCELLED"),
            "IN_PROGRESS", Set.of("PENDING_REVIEW", "CANCELLED"),
            "PENDING_REVIEW", Set.of("REVIEWED", "CANCELLED"),
            "REVIEWED", Set.of(),
            "CANCELLED", Set.of());

    private final WorkOrderMapper orderMapper;
    private final WorkOrderAssignmentMapper assignmentMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public OrderService(WorkOrderMapper orderMapper, WorkOrderAssignmentMapper assignmentMapper,
                        WorkOrderStatusHistoryMapper historyMapper, UserMapper userMapper, RoleMapper roleMapper) {
        this.orderMapper = orderMapper;
        this.assignmentMapper = assignmentMapper;
        this.historyMapper = historyMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    public OrderPage list(AuthenticatedUser actor, int page, int pageSize, String keyword, String status,
                          String startDate, String endDate) {
        LambdaQueryWrapper<WorkOrderEntity> query = scopedQuery(actor);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(WorkOrderEntity::getOrderNo, value)
                    .or().like(WorkOrderEntity::getCustomerName, value)
                    .or().like(WorkOrderEntity::getCustomerPhone, value));
        }
        if (status != null && !status.isBlank() && !"all".equalsIgnoreCase(status)) {
            String normalized = normalizeStatus(status);
            if ("PENDING_REVIEW".equals(normalized) && isCompletedAlias(status)) {
                query.in(WorkOrderEntity::getOrderStatus, "PENDING_REVIEW", "REVIEWED");
            } else {
                query.eq(WorkOrderEntity::getOrderStatus, normalized);
            }
        }
        if (startDate != null && !startDate.isBlank()) {
            query.ge(WorkOrderEntity::getCreatedAt, parseDateBoundary(startDate, false));
        }
        if (endDate != null && !endDate.isBlank()) {
            query.lt(WorkOrderEntity::getCreatedAt, parseDateBoundary(endDate, true));
        }
        query.orderByDesc(WorkOrderEntity::getCreatedAt).orderByDesc(WorkOrderEntity::getId);
        Page<WorkOrderEntity> result = orderMapper.selectPage(new Page<>(page, pageSize), query);
        return new OrderPage(toViews(result.getRecords()), result.getTotal(), page, pageSize);
    }

    public OrderView detail(AuthenticatedUser actor, long id) {
        WorkOrderEntity order = requireAccessible(actor, id);
        return toViews(List.of(order)).getFirst();
    }

    public List<InstallerView> installers(String keyword) {
        String value = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        return userMapper.selectList(new LambdaQueryWrapper<UserEntity>()
                        .eq(UserEntity::getDeleted, false)
                        .eq(UserEntity::getAccountStatus, "ENABLED")
                        .eq(UserEntity::getAuditStatus, "APPROVED")
                        .eq(UserEntity::getBlacklist, false)
                        .orderByAsc(UserEntity::getId)).stream()
                .filter(user -> roleMapper.selectRoleCodesByUserId(user.getId()).contains(RoleCode.INSTALLER.name()))
                .map(InstallerView::from)
                .filter(user -> value.isBlank() || (user.masterName() + " " + nullToEmpty(user.masterPhone()))
                        .toLowerCase(Locale.ROOT).contains(value))
                .toList();
    }

    @Transactional
    public OrderView create(AuthenticatedUser actor, OrderCommand command) {
        long installerId = requireSingleInstaller(command.masterIds());
        UserEntity customer = findCustomer(command.customerPhone());
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(newOrderNo());
        order.setOrderStatus("PENDING_VISIT");
        order.setCreatedBy(actor.userId());
        order.setVersion(0);
        order.setDeleted(false);
        apply(order, command, installerId, customer, actor.userId());
        orderMapper.insert(order);
        recordAssignment(order.getId(), installerId, actor.userId(), "创建订单并指派");
        recordStatus(order.getId(), null, "PENDING_VISIT", "创建订单", actor.userId());
        return detail(actor, order.getId());
    }

    @Transactional
    public OrderView update(AuthenticatedUser actor, long id, OrderCommand command) {
        WorkOrderEntity order = requireOrder(id);
        ensureEditable(order);
        long installerId = requireSingleInstaller(command.masterIds());
        UserEntity customer = findCustomer(command.customerPhone());
        long previousInstaller = order.getInstallerUserId();
        apply(order, command, installerId, customer, actor.userId());
        orderMapper.updateById(order);
        if (previousInstaller != installerId) {
            reassign(order.getId(), installerId, actor.userId(), "管理员编辑订单");
        }
        return detail(actor, id);
    }

    @Transactional
    public OrderView assign(AuthenticatedUser actor, long id, List<Long> masterIds, String reason) {
        WorkOrderEntity order = requireOrder(id);
        ensureEditable(order);
        long installerId = requireSingleInstaller(masterIds);
        if (!order.getInstallerUserId().equals(installerId)) {
            order.setInstallerUserId(installerId);
            order.setUpdatedBy(actor.userId());
            orderMapper.updateById(order);
            reassign(id, installerId, actor.userId(), reason == null ? "管理员重新指派" : reason.trim());
        }
        return detail(actor, id);
    }

    @Transactional
    public OrderView changeStatus(AuthenticatedUser actor, long id, String requestedStatus, String reason) {
        WorkOrderEntity order = requireOrder(id);
        String target = normalizeStatus(requestedStatus);
        String current = order.getOrderStatus();
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new BusinessException(409, "INVALID_ORDER_STATUS_TRANSITION",
                    "订单状态不能从" + statusLabel(current) + "变更为" + statusLabel(target));
        }
        order.setOrderStatus(target);
        order.setUpdatedBy(actor.userId());
        if ("CANCELLED".equals(target)) {
            order.setCancelledBy(actor.userId());
            order.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC));
            order.setCancelReason(blankToNull(reason));
        }
        orderMapper.updateById(order);
        recordStatus(id, current, target, blankToNull(reason), actor.userId());
        return detail(actor, id);
    }

    @Transactional
    public OrderView cancel(AuthenticatedUser actor, long id, String reason) {
        return changeStatus(actor, id, "CANCELLED", reason == null ? "管理员作废" : reason);
    }

    private LambdaQueryWrapper<WorkOrderEntity> scopedQuery(AuthenticatedUser actor) {
        LambdaQueryWrapper<WorkOrderEntity> query = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getDeleted, false);
        if (actor.hasRole(RoleCode.ADMIN)) return query;
        if (actor.hasRole(RoleCode.INSTALLER)) {
            return query.eq(WorkOrderEntity::getInstallerUserId, actor.userId());
        }
        return query.eq(WorkOrderEntity::getCustomerUserId, actor.userId());
    }

    private WorkOrderEntity requireAccessible(AuthenticatedUser actor, long id) {
        WorkOrderEntity order = orderMapper.selectOne(scopedQuery(actor).eq(WorkOrderEntity::getId, id));
        if (order == null) throw notFound();
        return order;
    }

    private WorkOrderEntity requireOrder(long id) {
        WorkOrderEntity order = orderMapper.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, id).eq(WorkOrderEntity::getDeleted, false));
        if (order == null) throw notFound();
        return order;
    }

    private void apply(WorkOrderEntity order, OrderCommand command, long installerId,
                       UserEntity customer, long actorId) {
        String phone = normalizePhone(command.customerPhone());
        String taskType = normalizeTaskType(command.taskType());
        List<String> area = command.addressArea() == null ? List.of() : command.addressArea();
        LocalDateTime start = parseDateTime(command.orderStartTime(), "上门开始时间");
        LocalDateTime end = parseDateTime(command.orderEndTime(), "预计结束时间");
        if (!end.isAfter(start)) {
            throw new BusinessException("INVALID_ORDER_TIME", "预计结束时间必须晚于上门开始时间");
        }
        order.setTaskType(taskType);
        order.setDescription(blankToNull(command.description()));
        order.setCustomerUserId(customer == null ? null : customer.getId());
        order.setCustomerName(command.customerName().trim());
        order.setCustomerPhone(phone);
        order.setInstallerUserId(installerId);
        order.setProvinceCode(area.get(0));
        order.setProvinceName(area.get(0));
        order.setCityCode(area.get(1));
        order.setCityName(area.get(1));
        order.setDistrictCode(area.get(2));
        order.setDistrictName(area.get(2));
        order.setDetailedAddress(command.addressDetail().trim());
        order.setRequiredStartAt(start);
        order.setExpectedEndAt(end);
        order.setAdminRemark(blankToNull(command.adminRemark()));
        order.setUpdatedBy(actorId);
    }

    private long requireSingleInstaller(List<Long> masterIds) {
        if (masterIds == null || masterIds.size() != 1 || masterIds.getFirst() == null) {
            throw new BusinessException("ONE_INSTALLER_REQUIRED", "一个订单必须且只能指派一位安装师傅");
        }
        long id = masterIds.getFirst();
        UserEntity user = userMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted()) || !"ENABLED".equals(user.getAccountStatus())
                || !"APPROVED".equals(user.getAuditStatus()) || Boolean.TRUE.equals(user.getBlacklist())
                || !roleMapper.selectRoleCodesByUserId(id).contains(RoleCode.INSTALLER.name())) {
            throw new BusinessException("INVALID_INSTALLER", "所选安装师傅不存在或账号不可用");
        }
        return id;
    }

    private UserEntity findCustomer(String rawPhone) {
        String phone = normalizePhone(rawPhone);
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone).eq(UserEntity::getDeleted, false));
        return user;
    }

    private void reassign(long orderId, long installerId, long actorId, String reason) {
        assignmentMapper.update(new LambdaUpdateWrapper<WorkOrderAssignmentEntity>()
                .eq(WorkOrderAssignmentEntity::getOrderId, orderId)
                .eq(WorkOrderAssignmentEntity::getIsActive, true)
                .set(WorkOrderAssignmentEntity::getIsActive, false)
                .set(WorkOrderAssignmentEntity::getUnassignedAt, LocalDateTime.now(ZoneOffset.UTC)));
        recordAssignment(orderId, installerId, actorId, reason);
    }

    private void recordAssignment(long orderId, long installerId, long actorId, String reason) {
        WorkOrderAssignmentEntity assignment = new WorkOrderAssignmentEntity();
        assignment.setOrderId(orderId);
        assignment.setInstallerUserId(installerId);
        assignment.setAssignedBy(actorId);
        assignment.setAssignedAt(LocalDateTime.now(ZoneOffset.UTC));
        assignment.setIsActive(true);
        assignment.setChangeReason(blankToNull(reason));
        assignmentMapper.insert(assignment);
    }

    private void recordStatus(long orderId, String from, String to, String reason, long actorId) {
        WorkOrderStatusHistoryEntity history = new WorkOrderStatusHistoryEntity();
        history.setOrderId(orderId);
        history.setFromStatus(from);
        history.setToStatus(to);
        history.setChangeReason(reason);
        history.setOperatorUserId(actorId);
        history.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        historyMapper.insert(history);
    }

    private List<OrderView> toViews(List<WorkOrderEntity> orders) {
        Set<Long> userIds = orders.stream()
                .flatMap(order -> java.util.stream.Stream.of(order.getCustomerUserId(), order.getInstallerUserId()))
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, UserEntity> users = userIds.isEmpty() ? Map.of() : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(UserEntity::getId, Function.identity()));
        return orders.stream().map(order -> toView(order, users)).toList();
    }

    private OrderView toView(WorkOrderEntity order, Map<Long, UserEntity> users) {
        UserEntity installer = users.get(order.getInstallerUserId());
        InstallerView master = installer == null
                ? new InstallerView(order.getInstallerUserId(), "安装师傅", null)
                : InstallerView.from(installer);
        List<String> area = java.util.stream.Stream.of(
                        order.getProvinceName(), order.getCityName(), order.getDistrictName())
                .filter(java.util.Objects::nonNull).toList();
        String taskLabel = TASK_LABELS.getOrDefault(order.getTaskType(), order.getTaskType());
        String address = String.join("", area) + order.getDetailedAddress();
        return new OrderView(order.getId(), order.getOrderNo(), taskLabel, order.getTaskType(),
                order.getDescription(), order.getCustomerUserId(), order.getCustomerName(), order.getCustomerPhone(),
                area, order.getDetailedAddress(), address, order.getRequiredStartAt(), order.getExpectedEndAt(),
                statusLabel(order.getOrderStatus()), order.getOrderStatus(), List.of(master), List.of(master),
                order.getAdminRemark(), order.getCancelReason(), order.getCreatedAt(), order.getUpdatedAt(),
                "空调服务", taskLabel, order.getDescription(), order.getCustomerName(), order.getCustomerPhone());
    }

    private String normalizePhone(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (value.startsWith("+86")) value = value.substring(3);
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BusinessException("INVALID_PHONE", "客户手机号格式不正确");
        }
        return value;
    }

    private String normalizeTaskType(String raw) {
        String value = raw == null ? "" : raw.trim();
        for (Map.Entry<String, String> entry : TASK_LABELS.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(value) || entry.getValue().equals(value)) return entry.getKey();
        }
        throw new BusinessException("INVALID_TASK_TYPE", "不支持的订单任务类型");
    }

    private String normalizeStatus(String raw) {
        String value = raw == null ? "" : raw.trim();
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("待上门", "PENDING_VISIT");
        aliases.put("处理中", "IN_PROGRESS");
        aliases.put("已完成", "PENDING_REVIEW");
        aliases.put("已作废", "CANCELLED");
        aliases.put("pending", "PENDING_VISIT");
        aliases.put("assigned", "PENDING_VISIT");
        aliases.put("processing", "IN_PROGRESS");
        aliases.put("completed", "PENDING_REVIEW");
        aliases.put("done", "PENDING_REVIEW");
        aliases.put("cancelled", "CANCELLED");
        aliases.put("canceled", "CANCELLED");
        String upper = value.toUpperCase(Locale.ROOT);
        if (STATUS_LABELS.containsKey(upper)) return upper;
        String code = aliases.get(value.toLowerCase(Locale.ROOT));
        if (code == null) code = aliases.get(value);
        if (code == null) throw new BusinessException("INVALID_ORDER_STATUS", "不支持的订单状态");
        return code;
    }

    private boolean isCompletedAlias(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return "已完成".equals(raw.trim()) || "completed".equals(value) || "done".equals(value);
    }

    private LocalDateTime parseDateTime(String raw, String fieldName) {
        if (raw == null || raw.isBlank()) throw new BusinessException("INVALID_ORDER_TIME", fieldName + "不能为空");
        try {
            return OffsetDateTime.parse(raw).withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        } catch (DateTimeParseException ignored) {
            try { return Instant.parse(raw).atOffset(ZoneOffset.UTC).toLocalDateTime(); }
            catch (DateTimeParseException ignoredAgain) {
                try { return LocalDateTime.parse(raw); }
                catch (DateTimeParseException exception) {
                    throw new BusinessException("INVALID_ORDER_TIME", fieldName + "格式不正确");
                }
            }
        }
    }

    private LocalDateTime parseDateBoundary(String raw, boolean nextDay) {
        try {
            LocalDate date = LocalDate.parse(raw);
            return (nextDay ? date.plusDays(1) : date).atStartOfDay();
        } catch (DateTimeParseException exception) {
            return parseDateTime(raw, "查询日期");
        }
    }

    private void ensureEditable(WorkOrderEntity order) {
        if ("CANCELLED".equals(order.getOrderStatus()) || "REVIEWED".equals(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_NOT_EDITABLE", "已结束的订单不能编辑或重新指派");
        }
    }

    private String newOrderNo() {
        String time = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now(ZoneOffset.UTC));
        return "WO" + time + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String statusLabel(String code) { return STATUS_LABELS.getOrDefault(code, code); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String nullToEmpty(String value) { return value == null ? "" : value; }
    private BusinessException notFound() { return new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在"); }

    public record OrderCommand(String taskType, String description, String customerName, String customerPhone,
                               List<String> addressArea, String addressDetail, String orderStartTime,
                               String orderEndTime, List<Long> masterIds, String adminRemark) { }
    public record OrderPage(List<OrderView> list, long total, int page, int pageSize) { }
    public record InstallerView(long id, String masterName, String masterPhone) {
        static InstallerView from(UserEntity user) {
            String name = user.getRealName();
            if (name == null || name.isBlank()) name = user.getNickname();
            if (name == null || name.isBlank()) name = user.getUsername();
            if (name == null || name.isBlank()) name = "安装师傅" + user.getId();
            return new InstallerView(user.getId(), name, user.getPhone());
        }
    }
    public record OrderView(long id, String orderNo, String taskType, String taskTypeCode, String description,
                            Long customerUserId, String customerName, String customerPhone, List<String> addressArea,
                            String addressDetail, String address, LocalDateTime orderStartTime,
                            LocalDateTime orderEndTime, String status, String statusCode,
                            List<InstallerView> selectedMasterList, List<InstallerView> masterList,
                            String adminRemark, String cancelReason, LocalDateTime createdAt, LocalDateTime updatedAt,
                            String serviceName, String productName, String productSpec, String name, String phone) { }
}
