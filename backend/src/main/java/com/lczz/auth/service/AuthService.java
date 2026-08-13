package com.lczz.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleEntity;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.UserRoleEntity;
import com.lczz.auth.persistence.UserRoleMapper;
import com.lczz.auth.persistence.WechatIdentityEntity;
import com.lczz.auth.persistence.WechatIdentityMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.auth.wechat.WechatIdentity;
import com.lczz.auth.wechat.WechatIdentityGateway;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.service.OrderCustomerBindingService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final WechatIdentityMapper wechatIdentityMapper;
    private final UserAccountService userAccountService;
    private final WechatIdentityGateway wechatGateway;
    private final LoginChallengeStore challengeStore;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OrderCustomerBindingService orderCustomerBindingService;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                       WechatIdentityMapper wechatIdentityMapper, UserAccountService userAccountService,
                       WechatIdentityGateway wechatGateway, LoginChallengeStore challengeStore,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       OrderCustomerBindingService orderCustomerBindingService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.wechatIdentityMapper = wechatIdentityMapper;
        this.userAccountService = userAccountService;
        this.wechatGateway = wechatGateway;
        this.challengeStore = challengeStore;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.orderCustomerBindingService = orderCustomerBindingService;
    }

    @Transactional
    public LoginResult passwordLogin(String username, String password, String ip) {
        UserEntity user = userAccountService.findByUsername(username.trim().toLowerCase(Locale.ROOT));
        if (user == null || user.getPasswordHash() == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(401, "BAD_CREDENTIALS", "用户名或密码错误");
        }
        AuthenticatedUser account = userAccountService.requireActive(user.getId());
        if (!account.hasRole(RoleCode.ADMIN)) {
            throw new BusinessException(403, "ADMIN_REQUIRED", "该账号无后台管理权限");
        }
        touchLogin(user.getId(), ip);
        return loginResult(account);
    }

    @Transactional
    public WechatLoginResult wechatLogin(String code, String ip) {
        WechatIdentity verified = wechatGateway.exchangeLoginCode(code);
        WechatIdentityEntity identity = findIdentity(verified);
        if (identity == null) {
            challengeStore.put(code, verified);
            return new WechatLoginResult(true, null);
        }
        touchIdentity(identity.getId());
        touchLogin(identity.getUserId(), ip);
        return new WechatLoginResult(false, loginResult(userAccountService.requireActive(identity.getUserId())));
    }

    @Transactional
    public LoginResult bindPhone(String loginCode, String phoneCode, String ip) {
        WechatIdentity verified = challengeStore.consume(loginCode);
        String phone = normalizePhone(wechatGateway.exchangePhoneCode(phoneCode));
        WechatIdentityEntity existingIdentity = findIdentity(verified);
        if (existingIdentity != null) {
            return completeExistingIdentity(existingIdentity, phone, ip);
        }
        UserEntity user = userAccountService.findByPhone(phone);
        boolean created = false;
        if (user == null) {
            user = new UserEntity();
            user.setNickname("微信用户");
            user.setPhone(phone);
            user.setAccountStatus("ENABLED");
            user.setAuditStatus("APPROVED");
            user.setBlacklist(false);
            user.setDeleted(false);
            try {
                userMapper.insert(user);
                created = true;
            } catch (DuplicateKeyException exception) {
                user = userAccountService.findByPhone(phone);
                if (user == null) throw exception;
            }
        }
        if (created) ensureRole(user.getId(), RoleCode.CUSTOMER);
        attachIdentity(user.getId(), verified);
        ensureCustomerRoleWhenUnassigned(user.getId());
        orderCustomerBindingService.bindPendingOrders(phone, user.getId());
        touchLogin(user.getId(), ip);
        return loginResult(userAccountService.requireActive(user.getId()));
    }

    public AuthenticatedUser current(AuthenticatedUser principal) {
        return userAccountService.requireActive(principal.userId());
    }

    private LoginResult completeExistingIdentity(WechatIdentityEntity identity, String phone, String ip) {
        UserEntity user = userMapper.selectById(identity.getUserId());
        if (user.getPhone() != null && !user.getPhone().equals(phone)) {
            throw new BusinessException(409, "WECHAT_PHONE_CONFLICT", "当前微信已绑定其他手机号");
        }
        if (user.getPhone() == null) {
            UserEntity phoneOwner = userAccountService.findByPhone(phone);
            if (phoneOwner != null && !phoneOwner.getId().equals(user.getId())) {
                throw new BusinessException(409, "PHONE_ALREADY_BOUND", "该手机号已绑定其他微信账号");
            }
            user.setPhone(phone);
            userMapper.updateById(user);
        }
        orderCustomerBindingService.bindPendingOrders(phone, user.getId());
        touchIdentity(identity.getId());
        touchLogin(user.getId(), ip);
        return loginResult(userAccountService.requireActive(user.getId()));
    }

    private void attachIdentity(long userId, WechatIdentity verified) {
        WechatIdentityEntity entity = new WechatIdentityEntity();
        entity.setUserId(userId);
        entity.setAppId(verified.appId());
        entity.setOpenId(verified.openId());
        entity.setUnionId(verified.unionId());
        entity.setSessionVersion(0);
        entity.setLastLoginAt(LocalDateTime.now(ZoneOffset.UTC));
        try {
            wechatIdentityMapper.insert(entity);
        } catch (DuplicateKeyException exception) {
            WechatIdentityEntity existing = findIdentity(verified);
            if (existing == null || existing.getUserId() != userId) throw exception;
        }
    }

    private void ensureRole(long userId, RoleCode roleCode) {
        RoleEntity role = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, roleCode.name()).eq(RoleEntity::getEnabled, true));
        if (role == null) throw new IllegalStateException("Required role is missing: " + roleCode);
        Long count = userRoleMapper.selectCount(new LambdaQueryWrapper<UserRoleEntity>()
                .eq(UserRoleEntity::getUserId, userId).eq(UserRoleEntity::getRoleId, role.getId()));
        if (count == 0) {
            UserRoleEntity link = new UserRoleEntity();
            link.setUserId(userId);
            link.setRoleId(role.getId());
            userRoleMapper.insert(link);
        }
    }

    private void ensureCustomerRoleWhenUnassigned(long userId) {
        if (roleMapper.selectRoleCodesByUserId(userId).isEmpty()) {
            ensureRole(userId, RoleCode.CUSTOMER);
        }
    }

    private WechatIdentityEntity findIdentity(WechatIdentity identity) {
        return wechatIdentityMapper.selectOne(new LambdaQueryWrapper<WechatIdentityEntity>()
                .eq(WechatIdentityEntity::getAppId, identity.appId())
                .eq(WechatIdentityEntity::getOpenId, identity.openId()));
    }

    private void touchIdentity(long id) {
        wechatIdentityMapper.update(new LambdaUpdateWrapper<WechatIdentityEntity>()
                .eq(WechatIdentityEntity::getId, id)
                .set(WechatIdentityEntity::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private void touchLogin(long userId, String ip) {
        userMapper.update(new LambdaUpdateWrapper<UserEntity>().eq(UserEntity::getId, userId)
                .set(UserEntity::getLastLoginAt, LocalDateTime.now(ZoneOffset.UTC))
                .set(UserEntity::getLastLoginIp, ip));
    }

    private String normalizePhone(String phone) {
        String normalized = phone == null ? "" : phone.replaceAll("\\s+", "");
        if (normalized.startsWith("+86")) normalized = normalized.substring(3);
        if (!PHONE_PATTERN.matcher(normalized).matches()) {
            throw new BusinessException(400, "INVALID_PHONE", "微信返回的手机号格式不合法");
        }
        return normalized;
    }

    private LoginResult loginResult(AuthenticatedUser user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResult(token.value(), "Bearer", token.expiresInSeconds(), UserInfo.from(user));
    }

    public record WechatLoginResult(boolean needPhone, LoginResult login) { }
    public record LoginResult(String token, String tokenType, long expiresIn, UserInfo userInfo) { }
    public record UserInfo(long id, String username, String name, String nickname, String phone,
                           String role, Set<String> roles) {
        public static UserInfo from(AuthenticatedUser user) {
            Set<String> roles = user.roles().stream().map(role -> role.name().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            String primary = user.roles().stream().sorted().findFirst().orElse(RoleCode.CUSTOMER)
                    .name().toLowerCase(Locale.ROOT);
            return new UserInfo(user.userId(), user.username(), user.displayName(), user.displayName(),
                    user.phone(), primary, roles);
        }
    }
}
