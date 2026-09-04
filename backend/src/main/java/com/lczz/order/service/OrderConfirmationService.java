package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.persistence.WorkOrderStatusHistoryEntity;
import com.lczz.order.persistence.WorkOrderStatusHistoryMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderConfirmationService {
    // Persisted event marker; keep stable so historical confirmations remain identifiable.
    public static final String CONFIRMATION_REASON = "客户确认订单完成";
    private final WorkOrderMapper orderMapper;
    private final WorkOrderStatusHistoryMapper historyMapper;

    public OrderConfirmationService(WorkOrderMapper orderMapper, WorkOrderStatusHistoryMapper historyMapper) {
        this.orderMapper = orderMapper;
        this.historyMapper = historyMapper;
    }

    @Transactional
    public ConfirmationView confirm(AuthenticatedUser actor, long orderId) {
        if (!actor.hasRole(RoleCode.CUSTOMER) && !actor.hasRole(RoleCode.DEALER)) {
            throw new BusinessException(403, "ORDER_CONFIRMATION_FORBIDDEN", "仅订单绑定客户可确认完成");
        }
        WorkOrderEntity order = orderMapper.selectForUpdate(orderId);
        if (order == null || !Objects.equals(order.getCustomerUserId(), actor.userId())) {
            throw new BusinessException(404, "ORDER_NOT_BOUND", "订单不存在或未绑定到当前用户");
        }
        if (!"IN_PROGRESS".equals(order.getOrderStatus())) {
            throw new BusinessException(409, "ORDER_NOT_CONFIRMABLE", "仅处理中订单可确认完成，请刷新订单状态");
        }
        LocalDateTime confirmedAt = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
        int updated = orderMapper.update(null, new LambdaUpdateWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getId, orderId)
                .eq(WorkOrderEntity::getCustomerUserId, actor.userId())
                .eq(WorkOrderEntity::getDeleted, false)
                .eq(WorkOrderEntity::getOrderStatus, "IN_PROGRESS")
                .eq(WorkOrderEntity::getVersion, order.getVersion())
                .set(WorkOrderEntity::getOrderStatus, "PENDING_REVIEW")
                .set(WorkOrderEntity::getUpdatedAt, confirmedAt)
                .set(WorkOrderEntity::getUpdatedBy, actor.userId())
                .set(WorkOrderEntity::getVersion, order.getVersion() + 1));
        if (updated != 1) throw new BusinessException(409, "ORDER_STATUS_CONFLICT", "订单状态已变化，请刷新后重试");

        WorkOrderStatusHistoryEntity history = new WorkOrderStatusHistoryEntity();
        history.setOrderId(orderId);
        history.setFromStatus("IN_PROGRESS");
        history.setToStatus("PENDING_REVIEW");
        history.setChangeReason(CONFIRMATION_REASON);
        history.setOperatorUserId(actor.userId());
        history.setCreatedAt(confirmedAt);
        historyMapper.insert(history);
        return new ConfirmationView(orderId, "已完成", "PENDING_REVIEW", actor.userId(), confirmedAt.atOffset(ZoneOffset.UTC));
    }

    public record ConfirmationView(long id, String status, String statusCode,
                                   long customerConfirmedBy, OffsetDateTime customerConfirmedAt) { }
}
