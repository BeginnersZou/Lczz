package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.persistence.RoleEntity;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.UserRoleEntity;
import com.lczz.auth.persistence.UserRoleMapper;
import com.lczz.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCustomerBindingService {
    private final WorkOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;

    public OrderCustomerBindingService(WorkOrderMapper orderMapper, UserMapper userMapper,
                                      RoleMapper roleMapper, UserRoleMapper userRoleMapper) {
        this.orderMapper = orderMapper;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
    }

    /** Pre-create only a new phone owner; never replace an existing account's role or profile. */
    @Transactional
    public UserEntity resolveAppointmentCustomer(String phone, String name, long actorId) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPhone, phone));
        if (user == null) {
            UserEntity created = new UserEntity();
            created.setPhone(phone);
            created.setNickname(name);
            created.setRealName(name);
            created.setAccountStatus("ENABLED");
            created.setAuditStatus("APPROVED");
            created.setBlacklist(false);
            created.setDeleted(false);
            created.setVersion(0);
            created.setCreatedBy(actorId);
            created.setUpdatedBy(actorId);
            try {
                userMapper.insert(created);
                RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                        .eq(RoleEntity::getRoleCode, "CUSTOMER").eq(RoleEntity::getEnabled, true));
                if (role == null) throw new IllegalStateException("CUSTOMER role is missing");
                UserRoleEntity link = new UserRoleEntity();
                link.setUserId(created.getId());
                link.setRoleId(role.getId());
                link.setCreatedBy(actorId);
                userRoleMapper.insert(link);
                user = created;
            } catch (DuplicateKeyException exception) {
                // A locking read sees a concurrently committed owner even with MySQL REPEATABLE READ.
                user = userMapper.selectByPhoneForUpdate(phone);
                if (user == null) throw exception;
            }
        }
        if (Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException(409, "CUSTOMER_UNAVAILABLE", "该客户手机号暂不可用，请联系管理员");
        }
        bindPendingOrders(phone, user.getId());
        return user;
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
