package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCustomerBindingService {
    private final WorkOrderMapper orderMapper;

    public OrderCustomerBindingService(WorkOrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Transactional
    public int bindPendingOrders(String phone, long userId) {
        if (phone == null || phone.isBlank()) return 0;
        return orderMapper.update(new LambdaUpdateWrapper<WorkOrderEntity>()
                .eq(WorkOrderEntity::getCustomerPhone, phone)
                .isNull(WorkOrderEntity::getCustomerUserId)
                .eq(WorkOrderEntity::getDeleted, false)
                .set(WorkOrderEntity::getCustomerUserId, userId));
    }
}
