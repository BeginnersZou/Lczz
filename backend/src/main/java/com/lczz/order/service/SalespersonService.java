package com.lczz.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.common.exception.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalespersonService {
    private final UserMapper users;
    private final RoleMapper roles;

    public SalespersonService(UserMapper users, RoleMapper roles) {
        this.users = users;
        this.roles = roles;
    }

    @Transactional(readOnly = true)
    public OptionPage options(int page, int pageSize, String keyword) {
        LambdaQueryWrapper<UserEntity> query = eligibleQuery();
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(part -> part.like(UserEntity::getRealName, value)
                    .or().like(UserEntity::getNickname, value)
                    .or().like(UserEntity::getPhone, value));
        }
        query.orderByAsc(UserEntity::getId);
        Page<UserEntity> found = users.selectPage(new Page<>(page, pageSize), query);
        return new OptionPage(found.getRecords().stream().map(this::toOption).toList(),
                found.getTotal(), page, pageSize);
    }

    @Transactional
    public Long requireEligible(Long userId) {
        if (userId == null) return null;
        // Serialize assignment with role change, disable, blacklist and soft delete.
        if (userId < 1 || users.selectForUpdate(userId) == null
                || users.selectCount(eligibleQuery().eq(UserEntity::getId, userId)) != 1) {
            throw new BusinessException(422, "INVALID_SALES_PERSON", "所选销售人员不存在或账号不可用");
        }
        return userId;
    }

    private LambdaQueryWrapper<UserEntity> eligibleQuery() {
        return new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleted, false)
                .eq(UserEntity::getAccountStatus, "ENABLED")
                .eq(UserEntity::getAuditStatus, "APPROVED")
                .eq(UserEntity::getBlacklist, false)
                .inSql(UserEntity::getId, "SELECT ur.user_id FROM sys_user_role ur "
                        + "JOIN sys_role r ON r.id=ur.role_id "
                        + "WHERE r.enabled=1 AND r.role_code='" + RoleCode.SALES.name() + "'");
    }

    private Option toOption(UserEntity user) {
        String name = user.getRealName();
        if (name == null || name.isBlank()) name = user.getNickname();
        if (name == null || name.isBlank()) name = "销售员" + user.getId();
        String phone = user.getPhone();
        String masked = phone != null && phone.matches("1\\d{10}")
                ? phone.substring(0, 3) + "****" + phone.substring(7) : null;
        return new Option(user.getId(), name, masked);
    }

    public record Option(long id, String name, String maskedPhone) { }
    public record OptionPage(List<Option> list, long total, int page, int pageSize) { }
}
