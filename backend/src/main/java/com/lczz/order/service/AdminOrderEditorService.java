package com.lczz.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.audit.OperationAuditService;
import com.lczz.common.exception.BusinessException;
import com.lczz.common.time.UtcTime;
import com.lczz.file.service.FileService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.progress.service.WorkProgressService;
import com.lczz.stocking.service.MaterialRequestService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminOrderEditorService {
    private static final String EDIT_OPERATION = "ORDER_ADMIN_EDIT";
    private final WorkOrderMapper orders;
    private final OrderService orderService;
    private final AdminOrderDetailService details;
    private final WorkProgressService progress;
    private final MaterialRequestService materials;
    private final FileService files;
    private final OperationAuditService audit;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AdminOrderEditorService(WorkOrderMapper orders, OrderService orderService, AdminOrderDetailService details,
                                    WorkProgressService progress, MaterialRequestService materials, FileService files,
                                    OperationAuditService audit, JdbcTemplate jdbc, ObjectMapper json) {
        this.orders = orders;
        this.orderService = orderService;
        this.details = details;
        this.progress = progress;
        this.materials = materials;
        this.files = files;
        this.audit = audit;
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public EditorView load(AuthenticatedUser actor, long orderId) {
        lock(actor, orderId);
        return view(actor, orderId);
    }

    @Transactional
    public EditorView save(AuthenticatedUser actor, long orderId, EditCommand command, String clientIp) {
        WorkOrderEntity order = lock(actor, orderId);
        if ("CANCELLED".equals(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_NOT_EDITABLE", "已作废订单不能修改");
        }
        String requestHash = hash(command);
        List<String> previous = jdbc.query("""
                SELECT after_json FROM operation_audit_log
                WHERE operation_type=? AND business_type='ORDER' AND business_id=?
                  AND operator_user_id=? AND request_id=? AND result_code='SUCCESS' ORDER BY id
                """, (rs, row) -> rs.getString(1), EDIT_OPERATION, Long.toString(orderId), actor.userId(), command.requestId());
        if (!previous.isEmpty()) {
            try {
                if (!requestHash.equals(json.readTree(previous.getFirst()).path("requestHash").asText())) {
                    throw new BusinessException(409, "EDIT_REQUEST_REUSED", "该提交标识已使用，请刷新后重试");
                }
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Invalid order edit receipt", e);
            }
            return view(actor, orderId);
        }
        Map<String, Object> before = snapshot(orderId);
        if (!Objects.equals(command.revision(), hash(before))) {
            throw new BusinessException(409, "ORDER_EDIT_CONFLICT", "订单、施工附件或备货清单已被修改，请重新加载后编辑");
        }
        List<StockSnapshot> stockBefore = command.materials() == null ? List.of() : lockMaterialStock(orderId, command.materials());
        if (command.order() != null) orderService.updateForAdminEditor(actor, orderId, command.order());
        if (command.attachmentFileIds() != null) {
            files.replaceOrderEditorFiles(actor, orderId,
                    new FileService.RelationCommand("ORDER", orderId, "ATTACHMENT", null), command.attachmentFileIds());
        }
        if (command.progress() != null) {
            if (command.progress().stream().map(ProgressEdit::id).distinct().count() != command.progress().size()) {
                throw new BusinessException("DUPLICATE_PROGRESS", "施工记录不能重复提交");
            }
            for (ProgressEdit entry : command.progress()) {
                progress.editForAdmin(actor, orderId, entry.id(), entry.description(), entry.fileIds());
            }
        }
        if (command.materials() != null) materials.editForAdmin(actor, orderId, command.materials());
        for (StockSnapshot stock : stockBefore) {
            BigDecimal afterStock = jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?", BigDecimal.class, stock.skuId());
            if (stock.stock().compareTo(afterStock) != 0) {
                audit.recordSuccess(actor.userId(), "ORDER_MATERIAL_STOCK_CHANGE", "PRODUCT", stock.productId(),
                        command.requestId(), clientIp, stock,
                        new StockSnapshot(stock.skuId(), stock.productId(), afterStock));
            }
        }
        Map<String, Object> after = snapshot(orderId);
        // A receipt is kept even for an unchanged save so retries cannot rebuild requests or links.
        audit.recordSuccess(actor.userId(), EDIT_OPERATION, "ORDER", orderId, command.requestId(), clientIp,
                auditSnapshot(before), Map.of("requestHash", requestHash, "snapshot", auditSnapshot(after)));
        return view(actor, orderId);
    }

    private WorkOrderEntity lock(AuthenticatedUser actor, long orderId) {
        if (actor == null || !actor.hasRole(RoleCode.ADMIN)) {
            throw new BusinessException(403, "ADMIN_ORDER_EDIT_FORBIDDEN", "仅管理员可以编辑订单");
        }
        WorkOrderEntity order = orders.selectForUpdate(orderId);
        if (order == null) throw new BusinessException(404, "ORDER_NOT_FOUND", "订单不存在");
        // Preparation endpoints lock the request. Take those same locks before checking the revision.
        jdbc.query("SELECT id FROM material_request WHERE order_id=? ORDER BY id FOR UPDATE", rs -> {}, orderId);
        return order;
    }

    private EditorView view(AuthenticatedUser actor, long orderId) {
        var detail = details.detail(actor, orderId);
        var active = detail.materialRequests().stream().filter(r -> !"VOIDED".equals(r.statusCode())).toList();
        if (active.size() > 1) {
            throw new BusinessException(409, "MULTIPLE_ACTIVE_MATERIAL_REQUESTS", "该订单存在多个有效耗材申请，请先核对备货数据");
        }
        List<EditAuditView> edits = jdbc.query("""
                SELECT a.operator_user_id,a.created_at,COALESCE(u.real_name,u.nickname,u.username,'管理员') operator_name
                FROM operation_audit_log a LEFT JOIN sys_user u ON u.id=a.operator_user_id
                WHERE a.operation_type=? AND a.business_type='ORDER' AND a.business_id=?
                  AND a.result_code='SUCCESS' ORDER BY a.id DESC LIMIT 1
                """, (rs, row) -> new EditAuditView(rs.getLong("operator_user_id"), rs.getString("operator_name"),
                rs.getObject("created_at", LocalDateTime.class)), EDIT_OPERATION, Long.toString(orderId));
        return new EditorView(detail.order(), detail.progress(), active.isEmpty() ? null : active.getFirst(),
                hash(snapshot(orderId)), !"CANCELLED".equals(detail.order().statusCode()), edits.isEmpty() ? null : edits.getFirst());
    }

    private List<StockSnapshot> lockMaterialStock(long orderId, MaterialRequestService.SubmitCommand command) {
        List<Long> selectedProducts = new ArrayList<>(jdbc.query("""
                SELECT DISTINCT i.product_id FROM material_request_item i JOIN material_request r ON r.id=i.request_id
                WHERE r.order_id=? AND r.request_status IN ('PENDING','PREPARING','DONE')
                """, (rs, row) -> rs.getLong(1), orderId));
        command.items().stream().map(MaterialRequestService.ItemCommand::productId).forEach(selectedProducts::add);
        if (selectedProducts.isEmpty()) return List.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(selectedProducts.size(), "?"));
        return jdbc.query("SELECT id,product_id,stock FROM product_sku WHERE product_id IN (" + placeholders + ") ORDER BY id FOR UPDATE",
                (rs, row) -> new StockSnapshot(rs.getLong("id"), rs.getLong("product_id"), rs.getBigDecimal("stock")), selectedProducts.toArray());
    }

    private Map<String, Object> snapshot(long orderId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("order", jdbc.queryForList("SELECT * FROM work_order WHERE id=?", orderId));
        result.put("progress", jdbc.queryForList("SELECT * FROM work_order_progress WHERE order_id=? ORDER BY id", orderId));
        result.put("files", jdbc.queryForList("""
                SELECT * FROM business_file_relation WHERE (business_type='ORDER' AND business_id=?)
                OR (business_type='PROGRESS' AND business_id IN (SELECT id FROM work_order_progress WHERE order_id=?)) ORDER BY id
                """, orderId, orderId));
        result.put("requests", jdbc.queryForList("SELECT * FROM material_request WHERE order_id=? ORDER BY id", orderId));
        result.put("items", jdbc.queryForList("""
                SELECT i.* FROM material_request_item i JOIN material_request r ON r.id=i.request_id
                WHERE r.order_id=? ORDER BY i.id
                """, orderId));
        return result;
    }

    private Map<String, Object> auditSnapshot(Map<String, Object> snapshot) {
        // Audit carries the changes, while phone numbers and appointment request hashes are omitted.
        Map<String, Object> result = new LinkedHashMap<>(snapshot);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orderRows = (List<Map<String, Object>>) snapshot.get("order");
        result.put("order", orderRows.stream().map(row -> {
            Map<String, Object> safe = new LinkedHashMap<>(row);
            safe.keySet().removeIf(key -> key.equalsIgnoreCase("customer_phone") || key.equalsIgnoreCase("dealer_request_hash"));
            return safe;
        }).toList());
        return result;
    }

    private String hash(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(value).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException e) {
            throw new IllegalStateException("Cannot create order edit revision", e);
        }
    }

    public record EditCommand(String requestId, String revision, OrderService.OrderCommand order,
                              List<Long> attachmentFileIds, List<ProgressEdit> progress,
                              MaterialRequestService.SubmitCommand materials) { }
    public record ProgressEdit(long id, String description, List<Long> fileIds) { }
    public record EditorView(OrderService.OrderView order, List<WorkProgressService.ProgressView> progress,
                             MaterialRequestService.RequestView materialRequest, String revision,
                             boolean editable, EditAuditView lastEdit) { }
    public record EditAuditView(long operatorUserId, String operatorName, @UtcTime LocalDateTime editedAt) { }
    private record StockSnapshot(long skuId, long productId, BigDecimal stock) { }
}
