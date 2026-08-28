package com.lczz.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleEntity;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.UserRoleEntity;
import com.lczz.auth.persistence.UserRoleMapper;
import java.util.Locale;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminBootstrapService {
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                                 PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean createIfMissing(String rawUsername, String rawPassword, String nickname) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        UserEntity existing = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, username));
        if (existing != null) return false;
        if (rawPassword.length() < 12) {
            throw new IllegalArgumentException("ADMIN_BOOTSTRAP_PASSWORD must contain at least 12 characters");
        }
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setNickname(nickname);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);

        RoleEntity admin = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, RoleCode.ADMIN.name()).eq(RoleEntity::getEnabled, true));
        if (admin == null) throw new IllegalStateException("ADMIN role is missing");
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(user.getId());
        link.setRoleId(admin.getId());
        userRoleMapper.insert(link);
        return true;
    }
}
