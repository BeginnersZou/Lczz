package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.common.exception.BusinessException;
import com.lczz.file.service.FileService;
import com.lczz.notification.service.SmsNotificationService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DealerAppointmentService {
    private final WorkOrderMapper orders;
    private final UserMapper users;
    private final OrderService orderService;
    private final OrderCustomerBindingService customers;
    private final FileService files;
    private final SalespersonService salespersonService;
    private final SmsNotificationService notifications;
    private final ObjectMapper json;

    public DealerAppointmentService(WorkOrderMapper orders, UserMapper users, OrderService orderService,
                                    OrderCustomerBindingService customers, FileService files,
                                    SalespersonService salespersonService,
                                    SmsNotificationService notifications, ObjectMapper json) {
        this.orders = orders;
        this.users = users;
        this.orderService = orderService;
        this.customers = customers;
        this.files = files;
        this.salespersonService = salespersonService;
        this.notifications = notifications;
        this.json = json;
    }

    @Transactional
    public Receipt create(AuthenticatedUser actor, Command command) {
        requireBookingActor(actor);
        boolean salesCreator = actor.hasRole(RoleCode.SALES);
        // Serialize retries for one creator before reading the idempotency key or creating a customer.
        if (users.selectForUpdate(actor.userId()) == null) {
            throw new BusinessException(403, "FORBIDDEN", "预约账号不可用");
        }
        Command normalized = new Command(command.requestId(), orderService.normalizeTaskType(command.taskType()),
                command.description() == null ? null : command.description().trim(), command.customerName().trim(),
                orderService.normalizePhone(command.customerPhone()), command.addressArea().stream().map(String::trim).toList(),
                command.addressDetail().trim(), command.fileIds() == null ? List.of() : List.copyOf(command.fileIds()),
                command.salesUserId());
        if (normalized.fileIds().size() > 9 || normalized.fileIds().stream().distinct().count() != normalized.fileIds().size()) {
            throw new BusinessException("INVALID_APPOINTMENT_FILES", "附件最多 9 个且不能重复");
        }
        String hash = fingerprint(normalized);
        LambdaQueryWrapper<WorkOrderEntity> retryQuery = new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getOrderSource, salesCreator ? "SALES_APPOINTMENT" : "DEALER_APPOINTMENT");
        if (salesCreator) retryQuery.eq(WorkOrderEntity::getCreatedBy, actor.userId())
                .eq(WorkOrderEntity::getSalesRequestId, command.requestId());
        else retryQuery.eq(WorkOrderEntity::getDealerUserId, actor.userId())
                .eq(WorkOrderEntity::getDealerRequestId, command.requestId());
        retryQuery.last("FOR UPDATE");
        WorkOrderEntity existing = orders.selectOne(retryQuery);
        if (existing != null) {
            if (!hash.equals(salesCreator ? existing.getSalesRequestHash() : existing.getDealerRequestHash())) {
                throw new BusinessException(409, "APPOINTMENT_REQUEST_CONFLICT", "同一提交编号不能用于不同预约内容");
            }
            return Receipt.from(existing);
        }

        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(orderService.newOrderNo());
        order.setOrderStatus("PENDING_ASSIGNMENT");
        order.setOrderSource(salesCreator ? "SALES_APPOINTMENT" : "DEALER_APPOINTMENT");
        if (salesCreator) {
            order.setSalesRequestId(normalized.requestId());
            order.setSalesRequestHash(hash);
        } else {
            order.setDealerUserId(actor.userId());
            order.setDealerRequestId(normalized.requestId());
            order.setDealerRequestHash(hash);
        }
        // An identical retry above must remain idempotent even if the salesperson was disabled later.
        order.setSalesUserId(salespersonService.requireEligible(normalized.salesUserId()));
        order.setTaskType(normalized.taskType());
        order.setDescription(normalized.description());
        order.setCustomerUserId(customers.resolveAppointmentCustomer(normalized.customerPhone(),
                normalized.customerName(), actor.userId()).getId());
        order.setCustomerName(normalized.customerName());
        order.setCustomerPhone(normalized.customerPhone());
        order.setProvinceName(normalized.addressArea().get(0));
        order.setCityName(normalized.addressArea().get(1));
        order.setDistrictName(normalized.addressArea().get(2));
        order.setDetailedAddress(normalized.addressDetail());
        order.setCreatedBy(actor.userId());
        order.setUpdatedBy(actor.userId());
        order.setVersion(0);
        order.setDeleted(false);
        orders.insert(order);
        files.bindAppointmentFiles(actor, order.getId(), normalized.fileIds());
        orderService.recordStatus(order.getId(), null, "PENDING_ASSIGNMENT",
                salesCreator ? "销售员提交预约安装" : "经销商提交预约安装", actor.userId());
        if (salesCreator) notifications.queueSalesAppointmentCreated(order.getId(), order.getOrderNo());
        else notifications.queueDealerAppointmentCreated(order.getId(), order.getOrderNo());
        return Receipt.from(order);
    }

    private String fingerprint(Command command) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(json.writeValueAsString(command).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | JsonProcessingException exception) {
            throw new IllegalStateException("Cannot fingerprint appointment", exception);
        }
    }

    private void requireBookingActor(AuthenticatedUser actor) {
        if (actor == null || !(actor.hasRole(RoleCode.DEALER) || actor.hasRole(RoleCode.SALES))) {
            throw new BusinessException(403, "FORBIDDEN", "仅经销商或销售员可以提交预约安装");
        }
    }

    public record Command(String requestId, String taskType, String description, String customerName,
                          String customerPhone, List<String> addressArea, String addressDetail, List<Long> fileIds,
                          Long salesUserId) {
        public Command(String requestId, String taskType, String description, String customerName,
                       String customerPhone, List<String> addressArea, String addressDetail, List<Long> fileIds) {
            this(requestId, taskType, description, customerName, customerPhone, addressArea, addressDetail,
                    fileIds, null);
        }
    }
    public record Receipt(long id, String orderNo, String statusCode, String orderSource) {
        static Receipt from(WorkOrderEntity order) {
            return new Receipt(order.getId(), order.getOrderNo(), order.getOrderStatus(), order.getOrderSource());
        }
    }
}
