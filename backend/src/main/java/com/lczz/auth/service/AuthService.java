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
import com.lczz.common.audit.OperationAuditService;
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
    private final OperationAuditService auditService;

    public AuthService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                       WechatIdentityMapper wechatIdentityMapper, UserAccountService userAccountService,
                       WechatIdentityGateway wechatGateway, LoginChallengeStore challengeStore,
                       PasswordEncoder passwordEncoder, JwtService jwtService,
                       OrderCustomerBindingService orderCustomerBindingService,
                       OperationAuditService auditService) {
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
        this.auditService = auditService;
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

    public UserInfo current(AuthenticatedUser principal) {
        return userInfo(userAccountService.requireActive(principal.userId()));
    }

    @Transactional
    public UserInfo updateProfile(AuthenticatedUser principal, String rawNickname, String rawRealName,
                                  String requestId, String clientIp) {
        AuthenticatedUser active = userAccountService.requireActive(principal.userId());
        UserEntity current = userMapper.selectForUpdate(active.userId());
        if (current == null || Boolean.TRUE.equals(current.getDeleted())) {
            throw new BusinessException(401, "UNAUTHORIZED", "登录状态已失效");
        }
        String nickname = rawNickname.trim();
        String realName = blankToNull(rawRealName);
        if (nickname.length() > 64) {
            throw new BusinessException("VALIDATION_ERROR", "昵称不能超过64字");
        }
        if (realName != null && realName.length() > 64) {
            throw new BusinessException("VALIDATION_ERROR", "真实姓名不能超过64字");
        }
        if (active.hasRole(RoleCode.INSTALLER) && realName == null) {
            throw new BusinessException("INSTALLER_REAL_NAME_REQUIRED", "安装师傅必须填写真实姓名");
        }
        ProfileSnapshot before = new ProfileSnapshot(current.getNickname(), current.getRealName());
        userMapper.update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, active.userId())
                .eq(UserEntity::getDeleted, false)
                .set(UserEntity::getNickname, nickname)
                .set(UserEntity::getRealName, realName)
                .set(UserEntity::getUpdatedBy, active.userId())
                .setSql("version = version + 1"));
        UserInfo result = userInfo(userAccountService.requireActive(active.userId()));
        auditService.recordSuccess(active.userId(), "SELF_PROFILE_UPDATE", "USER", active.userId(),
                requestId, clientIp, before, new ProfileSnapshot(result.nickname(), result.realName()));
        return result;
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

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }

    private LoginResult loginResult(AuthenticatedUser user) {
        JwtService.IssuedToken token = jwtService.issue(user);
        return new LoginResult(token.value(), "Bearer", token.expiresInSeconds(), userInfo(user));
    }

    private UserInfo userInfo(AuthenticatedUser user) {
        UserEntity entity = userMapper.selectById(user.userId());
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) {
            throw new BusinessException(401, "UNAUTHORIZED", "登录状态已失效");
        }
        return UserInfo.from(user, entity.getNickname(), entity.getRealName());
    }

    public record WechatLoginResult(boolean needPhone, LoginResult login) { }
    public record LoginResult(String token, String tokenType, long expiresIn, UserInfo userInfo) { }
    public record UserInfo(long id, String username, String name, String nickname, String realName, String phone,
                           String role, Set<String> roles) {
        public static UserInfo from(AuthenticatedUser user, String nickname, String realName) {
            Set<String> roles = user.roles().stream().map(role -> role.name().toLowerCase(Locale.ROOT))
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            String primary = user.roles().stream().sorted().findFirst().orElse(RoleCode.CUSTOMER)
                    .name().toLowerCase(Locale.ROOT);
            return new UserInfo(user.userId(), user.username(), user.displayName(), nickname, realName,
                    user.phone(), primary, roles);
        }
    }

    private record ProfileSnapshot(String nickname, String realName) { }
}
