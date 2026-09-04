package com.lczz.stocking.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.persistence.WorkOrderStatusHistoryEntity;
import com.lczz.order.persistence.WorkOrderStatusHistoryMapper;
import com.lczz.product.persistence.ProductEntity;
import com.lczz.product.persistence.ProductMapper;
import com.lczz.stocking.persistence.MaterialRequestEntity;
import com.lczz.stocking.persistence.MaterialRequestItemEntity;
import com.lczz.stocking.persistence.MaterialRequestItemMapper;
import com.lczz.stocking.persistence.MaterialRequestMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterialRequestService {
    private static final Set<String> ACTIVE_STATUSES = Set.of("PENDING", "PREPARING", "DONE");
    private static final Map<String, String> STATUS_LABELS = Map.of(
            "PENDING", "待备货", "PREPARING", "备货中", "DONE", "已备货", "VOIDED", "已作废");
    private final MaterialRequestMapper requestMapper;
    private final MaterialRequestItemMapper itemMapper;
    private final WorkOrderMapper orderMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;
    private final ProductMapper productMapper;

    public MaterialRequestService(MaterialRequestMapper requestMapper, MaterialRequestItemMapper itemMapper,
                                  WorkOrderMapper orderMapper, ProductMapper productMapper,
                                  WorkOrderStatusHistoryMapper historyMapper) {
        this.requestMapper = requestMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.productMapper = productMapper;
        this.historyMapper = historyMapper;
    }

    public RequestPage list(int page, int pageSize, String keyword, String status) {
        LambdaQueryWrapper<MaterialRequestEntity> query = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq(MaterialRequestEntity::getRequestStatus, normalizeStatus(status));
        }
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            Set<Long> orderIds = orderMapper.selectList(new LambdaQueryWrapper<WorkOrderEntity>()
                            .like(WorkOrderEntity::getOrderNo, value)
                            .or().like(WorkOrderEntity::getDescription, value))
                    .stream().map(WorkOrderEntity::getId).collect(Collectors.toSet());
            Set<Long> requestIds = itemMapper.selectList(new LambdaQueryWrapper<MaterialRequestItemEntity>()
                            .like(MaterialRequestItemEntity::getProductNameSnapshot, value)
                            .or().like(MaterialRequestItemEntity::getProductCodeSnapshot, value))
                    .stream().map(MaterialRequestItemEntity::getRequestId).collect(Collectors.toSet());
            if (orderIds.isEmpty() && requestIds.isEmpty()) return new RequestPage(List.of(), 0, page, pageSize);
            query.and(wrapper -> {
                if (!orderIds.isEmpty()) wrapper.in(MaterialRequestEntity::getOrderId, orderIds);
                if (!requestIds.isEmpty()) {
                    if (!orderIds.isEmpty()) wrapper.or();
                    wrapper.in(MaterialRequestEntity::getId, requestIds);
                }
            });
        }
        query.orderByDesc(MaterialRequestEntity::getSubmittedAt).orderByDesc(MaterialRequestEntity::getId);
        Page<MaterialRequestEntity> result = requestMapper.selectPage(new Page<>(page, pageSize), query);
        return new RequestPage(toViews(result.getRecords()), result.getTotal(), page, pageSize);
    }

    public RequestView detail(AuthenticatedUser actor, long requestId) {
        MaterialRequestEntity request = requireRequest(requestId);
        requireAccessibleOrder(actor, request.getOrderId());
        return toViews(List.of(request)).getFirst();
    }

    public List<RequestView> listByOrder(AuthenticatedUser actor, long orderId) {
        requireAccessibleOrder(actor, orderId);
        return toViews(requestMapper.selectList(new LambdaQueryWrapper<MaterialRequestEntity>()
                .eq(MaterialRequestEntity::getOrderId, orderId)
                .orderByDesc(MaterialRequestEntity::getSubmittedAt)
                .orderByDesc(MaterialRequestEntity::getId)));
    }

    public RequestView byOrder(AuthenticatedUser actor, long orderId) {
        requireAccessibleOrder(actor, orderId);
        MaterialRequestEntity request = requestMapper.selectOne(new LambdaQueryWrapper<MaterialRequestEntity>()
                .eq(MaterialRequestEntity::getOrderId, orderId)
                .orderByDesc(MaterialRequestEntity::getSubmittedAt)
                .orderByDesc(MaterialRequestEntity::getId)
                .last("LIMIT 1"));
        if (request == null) throw new BusinessException(404, "MATERIAL_REQUEST_NOT_FOUND", "该订单尚未提交耗材申请");
        return toViews(List.of(request)).getFirst();
    }

    @Transactional
    public RequestView submit(AuthenticatedUser actor, long orderId, SubmitCommand command) {
        WorkOrderEntity order = orderMapper.selectForUpdate(orderId);
        if (order == null || !java.util.Objects.equals(order.getInstallerUserId(), actor.userId())) {
            throw new BusinessException(404, "ORDER_NOT_ASSIGNED", "订单不存在或未指派给当前安装师傅");
        }
        if (!Set.of("PENDING_VISIT", "IN_PROGRESS").contains(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_NOT_ACCEPTING_MATERIALS", "当前订单状态不能提交耗材申请");
        }
        LinkedHashMap<Long, BigDecimal> requested = normalizeItems(command.items());
        MaterialRequestEntity existing = findActive(orderId);
        if (existing != null) {
            transitionToInProgress(order, actor.userId());
            return existingOrConflict(existing, actor.userId(), requested);
        }

        Map<Long, ProductEntity> productMap = new LinkedHashMap<>();
        requested.keySet().stream().sorted().forEach(productId -> {
            ProductEntity product = productMapper.selectForUpdate(productId);
            if (product != null && Boolean.TRUE.equals(product.getEnabled())) productMap.put(productId, product);
        });
        if (productMap.size() != requested.size()) {
            throw new BusinessException("INVALID_MATERIAL_PRODUCT", "所选耗材不存在或已下架");
        }
        requested.forEach((productId, quantity) -> {
            ProductEntity product = productMap.get(productId);
            BigDecimal stock = product.getDisplayStock() == null ? BigDecimal.ZERO : product.getDisplayStock();
            if (quantity.compareTo(stock) > 0) {
                throw new BusinessException(409, "INSUFFICIENT_PRODUCT_STOCK",
                        "耗材“" + product.getProductName() + "”库存仅剩" + stock.stripTrailingZeros().toPlainString()
                                + product.getUnit() + "，请调整申请数量");
            }
        });
        MaterialRequestEntity request = new MaterialRequestEntity();
        request.setRequestNo(newRequestNo());
        request.setOrderId(orderId);
        request.setInstallerUserId(actor.userId());
        request.setRequestStatus("PENDING");
        request.setRemark(blankToNull(command.remark()));
        request.setSubmittedAt(LocalDateTime.now(ZoneOffset.UTC));
        request.setVersion(0);
        try {
            requestMapper.insert(request);
        } catch (DuplicateKeyException exception) {
            MaterialRequestEntity concurrent = findActive(orderId);
            if (concurrent != null) return existingOrConflict(concurrent, actor.userId(), requested);
            throw exception;
        }
        requested.forEach((productId, quantity) -> reserveStock(productMap.get(productId), quantity, actor.userId()));
        requested.forEach((productId, quantity) -> insertSnapshot(request.getId(), productMap.get(productId), quantity));
        transitionToInProgress(order, actor.userId());
        return toViews(List.of(requestMapper.selectById(request.getId()))).getFirst();
    }

    private void transitionToInProgress(WorkOrderEntity order, long actorId) {
        if (!"PENDING_VISIT".equals(order.getOrderStatus())) return;
        order.setOrderStatus("IN_PROGRESS");
        order.setUpdatedBy(actorId);
        order.setVersion(order.getVersion() + 1);
        orderMapper.updateById(order);
        WorkOrderStatusHistoryEntity history = new WorkOrderStatusHistoryEntity();
        history.setOrderId(order.getId());
        history.setFromStatus("PENDING_VISIT");
        history.setToStatus("IN_PROGRESS");
        history.setChangeReason("安装师傅首次提交耗材申请");
        history.setOperatorUserId(actorId);
        history.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        historyMapper.insert(history);
    }

    @Transactional
    public RequestView prepare(AuthenticatedUser actor, long requestId, List<PreparedItemCommand> commands) {
        MaterialRequestEntity request = requireProcessable(requestId);
        List<MaterialRequestItemEntity> items = items(requestId);
        Map<Long, Boolean> checked = new HashMap<>();
        for (PreparedItemCommand command : commands) {
            if (checked.put(command.id(), command.checked()) != null) {
                throw new BusinessException("DUPLICATE_MATERIAL_ITEM", "备货明细不能重复");
            }
        }
        Set<Long> itemIds = items.stream().map(MaterialRequestItemEntity::getId).collect(Collectors.toSet());
        if (!checked.keySet().equals(itemIds)) {
            throw new BusinessException("INCOMPLETE_PREPARATION_ITEMS", "必须提交该申请的完整耗材清单");
        }
        boolean anyPrepared = false;
        for (MaterialRequestItemEntity item : items) {
            boolean prepared = Boolean.TRUE.equals(checked.get(item.getId()));
            item.setPreparedQuantity(prepared ? item.getRequestedQuantity() : BigDecimal.ZERO);
            item.setItemStatus(prepared ? "PREPARED" : "PENDING");
            itemMapper.updateById(item);
            anyPrepared |= prepared;
        }
        request.setRequestStatus(anyPrepared ? "PREPARING" : "PENDING");
        requestMapper.updateById(request);
        return detail(actor, requestId);
    }

    @Transactional
    public RequestView finish(AuthenticatedUser actor, long requestId) {
        MaterialRequestEntity request = requireRequest(requestId);
        if ("DONE".equals(request.getRequestStatus())) return detail(actor, requestId);
        if (!Set.of("PENDING", "PREPARING").contains(request.getRequestStatus())) {
            throw new BusinessException(409, "MATERIAL_REQUEST_NOT_PROCESSABLE", "该耗材申请不能完成备货");
        }
        boolean unfinished = items(requestId).stream().anyMatch(item -> !"PREPARED".equals(item.getItemStatus())
                || item.getPreparedQuantity().compareTo(item.getRequestedQuantity()) != 0);
        if (unfinished) {
            throw new BusinessException(409, "MATERIALS_NOT_FULLY_PREPARED", "请先完成全部耗材的备货确认");
        }
        request.setRequestStatus("DONE");
        request.setCompletedBy(actor.userId());
        request.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
        requestMapper.updateById(request);
        return detail(actor, requestId);
    }

    @Transactional
    public RequestView voidRequest(AuthenticatedUser actor, long requestId, String reason) {
        MaterialRequestEntity request = requireRequest(requestId);
        if ("VOIDED".equals(request.getRequestStatus())) return detail(actor, requestId);
        if ("DONE".equals(request.getRequestStatus())) {
            throw new BusinessException(409, "MATERIAL_REQUEST_ALREADY_DONE", "已完成的备货申请不能作废");
        }
        request.setRequestStatus("VOIDED");
        request.setVoidedBy(actor.userId());
        request.setVoidedAt(LocalDateTime.now(ZoneOffset.UTC));
        request.setVoidReason(blankToNull(reason));
        requestMapper.updateById(request);
        List<MaterialRequestItemEntity> requestItems = items(requestId);
        releaseReservedStock(requestItems, actor.userId());
        for (MaterialRequestItemEntity item : requestItems) {
            item.setItemStatus("VOIDED");
            item.setPreparedQuantity(BigDecimal.ZERO);
            itemMapper.updateById(item);
        }
        return detail(actor, requestId);
    }

    @Transactional
    public void voidActiveByOrder(AuthenticatedUser actor, long orderId, String reason) {
        MaterialRequestEntity request = requestMapper.selectOne(new LambdaQueryWrapper<MaterialRequestEntity>()
                .eq(MaterialRequestEntity::getOrderId, orderId)
                .in(MaterialRequestEntity::getRequestStatus, "PENDING", "PREPARING")
                .orderByDesc(MaterialRequestEntity::getId).last("LIMIT 1 FOR UPDATE"));
        if (request != null) voidRequest(actor, request.getId(), reason);
    }

    private RequestView existingOrConflict(MaterialRequestEntity existing, long installerId,
                                           LinkedHashMap<Long, BigDecimal> requested) {
        if (!existing.getInstallerUserId().equals(installerId) || !sameItems(existing.getId(), requested)) {
            throw new BusinessException(409, "ACTIVE_MATERIAL_REQUEST_EXISTS", "该订单已有未作废的耗材申请");
        }
        return toViews(List.of(existing)).getFirst();
    }

    private boolean sameItems(long requestId, Map<Long, BigDecimal> requested) {
        List<MaterialRequestItemEntity> existing = items(requestId);
        if (existing.size() != requested.size()) return false;
        return existing.stream().allMatch(item -> requested.containsKey(item.getProductId())
                && requested.get(item.getProductId()).compareTo(item.getRequestedQuantity()) == 0);
    }

    private LinkedHashMap<Long, BigDecimal> normalizeItems(List<ItemCommand> items) {
        if (items == null || items.isEmpty()) throw new BusinessException("MATERIAL_ITEMS_REQUIRED", "请选择所需耗材");
        LinkedHashMap<Long, BigDecimal> result = new LinkedHashMap<>();
        for (ItemCommand item : items) {
            if (result.put(item.productId(), item.quantity()) != null) {
                throw new BusinessException("DUPLICATE_MATERIAL_PRODUCT", "同一耗材不能重复提交");
            }
        }
        return result;
    }

    private void insertSnapshot(long requestId, ProductEntity product, BigDecimal quantity) {
        MaterialRequestItemEntity item = new MaterialRequestItemEntity();
        item.setRequestId(requestId);
        item.setProductId(product.getId());
        item.setProductCodeSnapshot(product.getProductCode());
        item.setProductNameSnapshot(product.getProductName());
        item.setModelSpecSnapshot(product.getModelSpec());
        item.setUnitSnapshot(product.getUnit());
        item.setDisplayPriceSnapshot(product.getDisplayPrice());
        item.setRequestedQuantity(quantity);
        item.setPreparedQuantity(BigDecimal.ZERO);
        item.setItemStatus("PENDING");
        item.setVersion(0);
        itemMapper.insert(item);
    }

    private void reserveStock(ProductEntity product, BigDecimal quantity, long actorId) {
        BigDecimal stock = product.getDisplayStock() == null ? BigDecimal.ZERO : product.getDisplayStock();
        product.setDisplayStock(stock.subtract(quantity));
        product.setUpdatedBy(actorId);
        product.setVersion((product.getVersion() == null ? 0 : product.getVersion()) + 1);
        productMapper.updateById(product);
    }

    private void releaseReservedStock(List<MaterialRequestItemEntity> requestItems, long actorId) {
        requestItems.stream().sorted(java.util.Comparator.comparing(MaterialRequestItemEntity::getProductId))
                .forEach(item -> {
                    ProductEntity product = productMapper.selectAnyForUpdate(item.getProductId());
                    if (product == null) return;
                    BigDecimal stock = product.getDisplayStock() == null ? BigDecimal.ZERO : product.getDisplayStock();
                    product.setDisplayStock(stock.add(item.getRequestedQuantity()));
                    product.setUpdatedBy(actorId);
                    product.setVersion((product.getVersion() == null ? 0 : product.getVersion()) + 1);
                    productMapper.updateById(product);
                });
    }

    private WorkOrderEntity requireAccessibleOrder(AuthenticatedUser actor, long orderId) {
        LambdaQueryWrapper<WorkOrderEntity> query = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, orderId).eq(WorkOrderEntity::getDeleted, false);
        if (!actor.hasRole(RoleCode.ADMIN)) {
            if (actor.hasRole(RoleCode.INSTALLER)) query.eq(WorkOrderEntity::getInstallerUserId, actor.userId());
            else query.eq(WorkOrderEntity::getCustomerUserId, actor.userId());
        }
        WorkOrderEntity order = orderMapper.selectOne(query);
        if (order == null) throw new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在");
        return order;
    }

    private MaterialRequestEntity requireRequest(long id) {
        MaterialRequestEntity request = requestMapper.selectById(id);
        if (request == null) throw new BusinessException(404, "MATERIAL_REQUEST_NOT_FOUND", "耗材申请不存在");
        return request;
    }

    private MaterialRequestEntity requireProcessable(long id) {
        MaterialRequestEntity request = requireRequest(id);
        if (!Set.of("PENDING", "PREPARING").contains(request.getRequestStatus())) {
            throw new BusinessException(409, "MATERIAL_REQUEST_NOT_PROCESSABLE", "该耗材申请不能继续备货");
        }
        return request;
    }

    private MaterialRequestEntity findActive(long orderId) {
        return requestMapper.selectOne(new LambdaQueryWrapper<MaterialRequestEntity>()
                .eq(MaterialRequestEntity::getOrderId, orderId)
                .in(MaterialRequestEntity::getRequestStatus, ACTIVE_STATUSES)
                .orderByDesc(MaterialRequestEntity::getId).last("LIMIT 1"));
    }

    private List<MaterialRequestItemEntity> items(long requestId) {
        return itemMapper.selectList(new LambdaQueryWrapper<MaterialRequestItemEntity>()
                .eq(MaterialRequestItemEntity::getRequestId, requestId)
                .orderByAsc(MaterialRequestItemEntity::getId));
    }

    private List<RequestView> toViews(List<MaterialRequestEntity> requests) {
        if (requests.isEmpty()) return List.of();
        Set<Long> orderIds = requests.stream().map(MaterialRequestEntity::getOrderId).collect(Collectors.toSet());
        Map<Long, WorkOrderEntity> orders = orderMapper.selectByIds(orderIds).stream()
                .collect(Collectors.toMap(WorkOrderEntity::getId, Function.identity()));
        Set<Long> requestIds = requests.stream().map(MaterialRequestEntity::getId).collect(Collectors.toSet());
        Map<Long, List<MaterialRequestItemEntity>> itemGroups = itemMapper.selectList(
                        new LambdaQueryWrapper<MaterialRequestItemEntity>()
                                .in(MaterialRequestItemEntity::getRequestId, requestIds)
                                .orderByAsc(MaterialRequestItemEntity::getId)).stream()
                .collect(Collectors.groupingBy(MaterialRequestItemEntity::getRequestId));
        Set<Long> productIds = itemGroups.values().stream().flatMap(List::stream)
                .map(MaterialRequestItemEntity::getProductId).collect(Collectors.toSet());
        Map<Long, ProductEntity> products = productIds.isEmpty() ? Map.of() : productMapper.selectByIds(productIds)
                .stream().collect(Collectors.toMap(ProductEntity::getId, Function.identity()));
        return requests.stream().map(request -> toView(request, orders.get(request.getOrderId()),
                itemGroups.getOrDefault(request.getId(), List.of()), products)).toList();
    }

    private RequestView toView(MaterialRequestEntity request, WorkOrderEntity order,
                               List<MaterialRequestItemEntity> items, Map<Long, ProductEntity> products) {
        List<MaterialView> materials = items.stream().map(item -> {
            ProductEntity product = products.get(item.getProductId());
            BigDecimal stock = product == null ? null : product.getDisplayStock();
            return new MaterialView(item.getId(), item.getProductId(), item.getProductNameSnapshot(),
                    item.getModelSpecSnapshot(), item.getRequestedQuantity(), item.getUnitSnapshot(),
                    "PREPARED".equals(item.getItemStatus()), stock, item.getPreparedQuantity(),
                    item.getDisplayPriceSnapshot(), item.getItemStatus());
        }).toList();
        String productName = order == null ? "安装订单" : firstNonBlank(order.getDescription(), order.getTaskType(), "安装订单");
        String orderNo = order == null ? null : order.getOrderNo();
        String status = request.getRequestStatus().toLowerCase(Locale.ROOT);
        return new RequestView(request.getId(), request.getRequestNo(), request.getOrderId(), orderNo,
                productName, status, request.getRequestStatus(), STATUS_LABELS.get(request.getRequestStatus()),
                request.getRemark(), materials, request.getSubmittedAt(), request.getSubmittedAt(),
                request.getCompletedBy(), request.getCompletedAt(), request.getVoidedBy(), request.getVoidedAt(),
                request.getVoidReason());
    }

    private String normalizeStatus(String raw) {
        String value = raw.trim().toUpperCase(Locale.ROOT);
        Map<String, String> aliases = Map.of("待备货", "PENDING", "备货中", "PREPARING", "已备货", "DONE", "已作废", "VOIDED");
        String normalized = aliases.getOrDefault(raw.trim(), value);
        if (!STATUS_LABELS.containsKey(normalized)) throw new BusinessException("INVALID_PREPARATION_STATUS", "备货状态不合法");
        return normalized;
    }

    private String newRequestNo() {
        String time = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .format(LocalDateTime.now(ZoneOffset.UTC));
        return "MR" + time + UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase(Locale.ROOT);
    }

    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return "安装订单";
    }

    public record ItemCommand(long productId, BigDecimal quantity) { }
    public record SubmitCommand(List<ItemCommand> items, String remark) { }
    public record PreparedItemCommand(long id, boolean checked) { }
    public record RequestPage(List<RequestView> list, long total, int page, int pageSize) { }
    public record MaterialView(long id, long productId, String name, String spec, BigDecimal count, String unit,
                               boolean checked, BigDecimal stock, BigDecimal preparedQuantity,
                               BigDecimal displayPrice, String itemStatus) { }
    public record RequestView(long id, String requestNo, long orderId, String orderNo, String productName,
                              String status, String statusCode, String statusLabel, String remark,
                              List<MaterialView> materials, LocalDateTime createTime, LocalDateTime submittedAt,
                              Long completedBy, LocalDateTime completedAt, Long voidedBy, LocalDateTime voidedAt,
                              String voidReason) { }
}
