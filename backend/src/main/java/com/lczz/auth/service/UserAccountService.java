package com.lczz.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.common.exception.BusinessException;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class UserAccountService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    public UserAccountService(UserMapper userMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
    }

    public UserEntity findByUsername(String username) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username).eq(UserEntity::getDeleted, false));
    }

    public UserEntity findByPhone(String phone) {
        return userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, phone).eq(UserEntity::getDeleted, false));
    }

    public AuthenticatedUser requireActive(long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) {
            throw new BusinessException(401, "UNAUTHORIZED", "登录状态已失效");
        }
        if (!"ENABLED".equals(user.getAccountStatus()) || !"APPROVED".equals(user.getAuditStatus())
                || Boolean.TRUE.equals(user.getBlacklist())) {
            throw new BusinessException(403, "ACCOUNT_UNAVAILABLE", "账号不可用，请联系管理员");
        }
        EnumSet<RoleCode> roles = EnumSet.noneOf(RoleCode.class);
        roleMapper.selectRoleCodesByUserId(userId).forEach(code -> roles.add(RoleCode.valueOf(code)));
        if (roles.isEmpty()) {
            throw new BusinessException(403, "ROLE_MISSING", "账号尚未分配角色");
        }
        String name = firstNonBlank(user.getNickname(), user.getRealName(), user.getUsername(), "用户" + userId);
        Set<RoleCode> immutableRoles = Collections.unmodifiableSet(EnumSet.copyOf(roles));
        return new AuthenticatedUser(userId, user.getUsername(), name, user.getPhone(), immutableRoles);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "用户";
    }
}
