package com.lczz.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.config.DevelopmentAuthProperties;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleEntity;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.UserRoleEntity;
import com.lczz.auth.persistence.UserRoleMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.common.exception.BusinessException;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("local")
@ConditionalOnProperty(prefix = "lczz.dev-auth", name = "enabled", havingValue = "true")
public class DevelopmentAuthService {
    private static final List<TestAccount> TEST_ACCOUNTS = List.of(
            new TestAccount("admin-test", "联调管理员", "19000000001", RoleCode.ADMIN),
            new TestAccount("installer-test", "联调安装师傅", "19000000002", RoleCode.INSTALLER),
            new TestAccount("customer-test", "联调客户", "19000000003", RoleCode.CUSTOMER),
            new TestAccount("dealer-test", "联调经销商", "19000000004", RoleCode.DEALER));

    private final DevelopmentAuthProperties properties;
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final UserAccountService userAccountService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public DevelopmentAuthService(DevelopmentAuthProperties properties, UserMapper userMapper,
                                  RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                                  UserAccountService userAccountService, PasswordEncoder passwordEncoder,
                                  JwtService jwtService) {
        this.properties = properties;
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.userAccountService = userAccountService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public void initializeAccounts() {
        properties.validate();
        for (TestAccount account : TEST_ACCOUNTS) upsert(account);
    }

    @Transactional(readOnly = true)
    public AuthService.LoginResult login(String rawUsername, String rawPassword) {
        String username = rawUsername.trim().toLowerCase(Locale.ROOT);
        UserEntity user = userAccountService.findByUsername(username);
        if (user == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(rawPassword, user.getPasswordHash())
                || TEST_ACCOUNTS.stream().noneMatch(account -> account.username().equals(username))) {
            throw new BusinessException(401, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        AuthenticatedUser account = userAccountService.requireActive(user.getId());
        JwtService.IssuedToken token = jwtService.issue(account);
        return new AuthService.LoginResult(token.value(), "Bearer", token.expiresInSeconds(),
                AuthService.UserInfo.from(account));
    }

    private void upsert(TestAccount account) {
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, account.username()));
        if (user == null) {
            user = new UserEntity();
            user.setUsername(account.username());
            user.setPhone(account.phone());
        }
        user.setPasswordHash(passwordEncoder.encode(properties.password()));
        user.setNickname(account.displayName());
        user.setRealName(account.displayName());
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        if (user.getId() == null) userMapper.insert(user); else userMapper.updateById(user);
        assignOnlyRole(user.getId(), account.role());
    }

    private void assignOnlyRole(long userId, RoleCode roleCode) {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode.name()).eq(RoleEntity::getEnabled, true));
        if (role == null) throw new IllegalStateException("Required role is missing: " + roleCode);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(userId);
        link.setRoleId(role.getId());
        userRoleMapper.insert(link);
    }

    private record TestAccount(String username, String displayName, String phone, RoleCode role) { }
}
