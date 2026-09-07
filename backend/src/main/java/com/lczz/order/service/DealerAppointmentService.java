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
    private final SmsNotificationService notifications;
    private final ObjectMapper json;

    public DealerAppointmentService(WorkOrderMapper orders, UserMapper users, OrderService orderService,
                                    OrderCustomerBindingService customers, FileService files,
                                    SmsNotificationService notifications, ObjectMapper json) {
        this.orders = orders;
        this.users = users;
        this.orderService = orderService;
        this.customers = customers;
        this.files = files;
        this.notifications = notifications;
        this.json = json;
    }

    @Transactional
    public Receipt create(AuthenticatedUser actor, Command command) {
        requireDealer(actor);
        // Serialize retries for one dealer before reading the idempotency key or creating a customer.
        if (users.selectForUpdate(actor.userId()) == null) {
            throw new BusinessException(403, "FORBIDDEN", "经销商账号不可用");
        }
        Command normalized = new Command(command.requestId(), orderService.normalizeTaskType(command.taskType()),
                command.description() == null ? null : command.description().trim(), command.customerName().trim(),
                orderService.normalizePhone(command.customerPhone()), command.addressArea().stream().map(String::trim).toList(),
                command.addressDetail().trim(), command.fileIds() == null ? List.of() : List.copyOf(command.fileIds()));
        if (normalized.fileIds().size() > 9 || normalized.fileIds().stream().distinct().count() != normalized.fileIds().size()) {
            throw new BusinessException("INVALID_APPOINTMENT_FILES", "附件最多 9 个且不能重复");
        }
        String hash = fingerprint(normalized);
        WorkOrderEntity existing = orders.selectOne(new LambdaQueryWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getDealerUserId, actor.userId())
                .eq(WorkOrderEntity::getDealerRequestId, command.requestId()).last("FOR UPDATE"));
        if (existing != null) {
            if (!hash.equals(existing.getDealerRequestHash())) {
                throw new BusinessException(409, "APPOINTMENT_REQUEST_CONFLICT", "同一提交编号不能用于不同预约内容");
            }
            return Receipt.from(existing);
        }

        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(orderService.newOrderNo());
        order.setOrderStatus("PENDING_ASSIGNMENT");
        order.setOrderSource("DEALER_APPOINTMENT");
        order.setDealerUserId(actor.userId());
        order.setDealerRequestId(normalized.requestId());
        order.setDealerRequestHash(hash);
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
        files.bindDealerAppointmentFiles(actor, order.getId(), normalized.fileIds());
        orderService.recordStatus(order.getId(), null, "PENDING_ASSIGNMENT", "经销商提交预约安装", actor.userId());
        notifications.queueDealerAppointmentCreated(order.getId(), order.getOrderNo());
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

    private void requireDealer(AuthenticatedUser actor) {
        if (actor == null || !actor.hasRole(RoleCode.DEALER)) {
            throw new BusinessException(403, "FORBIDDEN", "仅经销商可以提交预约安装");
        }
    }

    public record Command(String requestId, String taskType, String description, String customerName,
                          String customerPhone, List<String> addressArea, String addressDetail, List<Long> fileIds) { }
    public record Receipt(long id, String orderNo, String statusCode, String orderSource) {
        static Receipt from(WorkOrderEntity order) {
            return new Receipt(order.getId(), order.getOrderNo(), order.getOrderStatus(), order.getOrderSource());
        }
    }
}
