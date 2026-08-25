package com.lczz.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.RoleEntity;
import com.lczz.auth.persistence.RoleMapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.UserRoleCodeRow;
import com.lczz.auth.persistence.UserRoleEntity;
import com.lczz.auth.persistence.UserRoleMapper;
import com.lczz.common.audit.OperationAuditService;
import com.lczz.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserManagementService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ENABLED", "DISABLED");
    private static final List<String> ROLE_PRIORITY = List.of("ADMIN", "INSTALLER", "DEALER", "CUSTOMER");
    private static final Map<String, String> GENDER_ALIASES = Map.of(
            "男", "MALE", "女", "FEMALE", "未知", "UNKNOWN");

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final OperationAuditService auditService;

    public UserManagementService(UserMapper userMapper, RoleMapper roleMapper, UserRoleMapper userRoleMapper,
                                 OperationAuditService auditService) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public UserPage list(int page, int pageSize, String keyword, String role, String accountStatus,
                         Boolean blacklist) {
        RoleCode roleFilter = role == null || role.isBlank() ? null : normalizeRole(role);
        String statusFilter = accountStatus == null || accountStatus.isBlank()
                ? null : normalizeAccountStatus(accountStatus);
        LambdaQueryWrapper<UserEntity> query = new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getDeleted, false);
        if (keyword != null && !keyword.isBlank()) {
            String value = keyword.trim();
            query.and(wrapper -> wrapper.like(UserEntity::getNickname, value)
                    .or().like(UserEntity::getRealName, value)
                    .or().like(UserEntity::getPhone, value));
        }
        if (roleFilter != null) {
            query.inSql(UserEntity::getId, "SELECT ur.user_id FROM sys_user_role ur "
                    + "JOIN sys_role r ON r.id = ur.role_id WHERE r.enabled = TRUE AND r.role_code = '"
                    + roleFilter.name() + "'");
        }
        if (statusFilter != null) query.eq(UserEntity::getAccountStatus, statusFilter);
        if (blacklist != null) query.eq(UserEntity::getBlacklist, blacklist);
        query.orderByDesc(UserEntity::getCreatedAt).orderByDesc(UserEntity::getId);
        Page<UserEntity> result = userMapper.selectPage(new Page<>(page, pageSize), query);
        return new UserPage(toViews(result.getRecords()), result.getTotal(), page, pageSize);
    }

    @Transactional(readOnly = true)
    public UserView detail(long id) {
        UserEntity user = requireUser(id);
        return toViews(List.of(user)).getFirst();
    }

    @Transactional
    public UserView create(AuthenticatedUser actor, CreateCommand command, AuditContext context) {
        RoleCode role = normalizeRole(command.role());
        String nickname = command.nickname().trim();
        String realName = blankToNull(command.realName());
        String gender = normalizeGender(command.gender());
        String phone = normalizePhone(command.phone());
        if (userMapper.selectCount(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getPhone, phone)) > 0) {
            throw phoneAlreadyExists();
        }

        RoleEntity roleEntity = requireEnabledRole(role);
        UserEntity user = new UserEntity();
        user.setNickname(nickname);
        user.setRealName(realName);
        user.setGender(gender);
        user.setPhone(phone);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setVersion(0);
        user.setDeleted(false);
        user.setCreatedBy(actor.userId());
        user.setUpdatedBy(actor.userId());
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw phoneAlreadyExists();
        }

        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(user.getId());
        link.setRoleId(roleEntity.getId());
        link.setCreatedBy(actor.userId());
        userRoleMapper.insert(link);

        UserView result = detail(user.getId());
        auditService.recordSuccess(actor.userId(), "USER_CREATE", "USER", user.getId(),
                context.requestId(), context.clientIp(), null, snapshot(result));
        return result;
    }

    @Transactional
    public UserView update(AuthenticatedUser actor, long id, UpdateCommand command, AuditContext context) {
        RoleCode nextRole = normalizeRole(command.role());
        requireUser(id);
        List<String> currentRoles = roleMapper.selectRoleCodesByUserId(id);
        if (actor.userId() == id && nextRole != RoleCode.ADMIN) {
            throw new BusinessException(409, "SELF_ROLE_CHANGE_FORBIDDEN", "管理员不能修改自己的管理员角色");
        }
        if (currentRoles.contains(RoleCode.ADMIN.name()) && nextRole != RoleCode.ADMIN) {
            ensureAdminContinuity(id);
        }
        UserEntity current = lockUser(id);
        currentRoles = normalizedRoles(roleMapper.selectRoleCodesByUserId(id));
        AuditSnapshot before = snapshot(current, currentRoles);
        String nickname = command.nickname().trim();
        String realName = blankToNull(command.realName());
        String gender = normalizeGender(command.gender());
        userMapper.update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, id)
                .set(UserEntity::getNickname, nickname)
                .set(UserEntity::getRealName, realName)
                .set(UserEntity::getGender, gender)
                .set(UserEntity::getUpdatedBy, actor.userId())
                .setSql("version = version + 1"));
        replaceRole(actor.userId(), id, currentRoles, nextRole);
        UserView result = detail(id);
        auditService.recordSuccess(actor.userId(), "USER_UPDATE", "USER", id,
                context.requestId(), context.clientIp(), before, snapshot(result));
        return result;
    }

    @Transactional
    public UserView changeStatus(AuthenticatedUser actor, long id, String requestedStatus, AuditContext context) {
        String nextStatus = normalizeAccountStatus(requestedStatus);
        UserEntity current = requireUser(id);
        List<String> currentRoles = normalizedRoles(roleMapper.selectRoleCodesByUserId(id));
        if (actor.userId() == id && "DISABLED".equals(nextStatus)) {
            throw new BusinessException(409, "SELF_STATUS_CHANGE_FORBIDDEN", "管理员不能停用自己的账号");
        }
        if (currentRoles.contains(RoleCode.ADMIN.name()) && "DISABLED".equals(nextStatus)
                && "ENABLED".equals(current.getAccountStatus())) {
            ensureAdminContinuity(id);
        }
        current = lockUser(id);
        AuditSnapshot before = snapshot(current, currentRoles);
        userMapper.update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, id)
                .set(UserEntity::getAccountStatus, nextStatus)
                .set(UserEntity::getUpdatedBy, actor.userId())
                .setSql("version = version + 1"));
        UserView result = detail(id);
        auditService.recordSuccess(actor.userId(), "USER_STATUS_CHANGE", "USER", id,
                context.requestId(), context.clientIp(), before, snapshot(result));
        return result;
    }

    @Transactional
    public UserView changeBlacklist(AuthenticatedUser actor, long id, boolean blacklist, String reason,
                                    AuditContext context) {
        UserEntity current = requireUser(id);
        List<String> currentRoles = normalizedRoles(roleMapper.selectRoleCodesByUserId(id));
        if (actor.userId() == id && blacklist) {
            throw new BusinessException(409, "SELF_BLACKLIST_FORBIDDEN", "管理员不能将自己加入黑名单");
        }
        if (currentRoles.contains(RoleCode.ADMIN.name()) && blacklist && !Boolean.TRUE.equals(current.getBlacklist())) {
            ensureAdminContinuity(id);
        }
        current = lockUser(id);
        AuditSnapshot before = snapshot(current, currentRoles);
        userMapper.update(new LambdaUpdateWrapper<UserEntity>()
                .eq(UserEntity::getId, id)
                .set(UserEntity::getBlacklist, blacklist)
                .set(UserEntity::getUpdatedBy, actor.userId())
                .setSql("version = version + 1"));
        UserView result = detail(id);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("user", snapshot(result));
        after.put("reason", reason.trim());
        auditService.recordSuccess(actor.userId(), "USER_BLACKLIST_CHANGE", "USER", id,
                context.requestId(), context.clientIp(), before, after);
        return result;
    }

    private void replaceRole(long actorId, long userId, List<String> currentRoles, RoleCode nextRole) {
        if (currentRoles.size() == 1 && currentRoles.contains(nextRole.name())) return;
        RoleEntity role = requireEnabledRole(nextRole);
        userRoleMapper.delete(new LambdaQueryWrapper<UserRoleEntity>().eq(UserRoleEntity::getUserId, userId));
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(userId);
        link.setRoleId(role.getId());
        link.setCreatedBy(actorId);
        userRoleMapper.insert(link);
    }

    private RoleEntity requireEnabledRole(RoleCode role) {
        RoleEntity entity = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getRoleCode, role.name())
                .eq(RoleEntity::getEnabled, true));
        if (entity == null) {
            throw new BusinessException(409, "ROLE_UNAVAILABLE", "目标角色当前不可用");
        }
        return entity;
    }

    private void ensureAdminContinuity(long targetUserId) {
        List<Long> activeAdminIds = roleMapper.selectActiveAdminIdsForUpdate();
        if (activeAdminIds.contains(targetUserId) && activeAdminIds.size() <= 1) {
            throw new BusinessException(409, "LAST_ADMIN_REQUIRED", "系统必须至少保留一个有效管理员");
        }
    }

    private UserEntity lockUser(long id) {
        UserEntity user = userMapper.selectForUpdate(id);
        if (user == null) throw notFound();
        return user;
    }

    private UserEntity requireUser(long id) {
        UserEntity user = userMapper.selectById(id);
        if (user == null || Boolean.TRUE.equals(user.getDeleted())) throw notFound();
        return user;
    }

    private List<UserView> toViews(List<UserEntity> users) {
        if (users.isEmpty()) return List.of();
        Map<Long, List<String>> roles = rolesByUserIds(users.stream().map(UserEntity::getId).toList());
        return users.stream().map(user -> toView(user, roles.getOrDefault(user.getId(), List.of()))).toList();
    }

    private Map<Long, List<String>> rolesByUserIds(Collection<Long> userIds) {
        Map<Long, List<String>> result = userIds.stream().collect(Collectors.toMap(
                Function.identity(), ignored -> new ArrayList<>(), (left, right) -> left, LinkedHashMap::new));
        for (UserRoleCodeRow row : roleMapper.selectRoleCodesByUserIds(userIds)) {
            result.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row.getRoleCode());
        }
        result.replaceAll((ignored, values) -> normalizedRoles(values));
        return result;
    }

    private List<String> normalizedRoles(Collection<String> values) {
        return values.stream().filter(Objects::nonNull).map(value -> value.toUpperCase(Locale.ROOT))
                .filter(value -> ROLE_PRIORITY.contains(value)).distinct()
                .sorted(Comparator.comparingInt(ROLE_PRIORITY::indexOf)).toList();
    }

    private UserView toView(UserEntity user, List<String> roles) {
        String primaryRole = roles.isEmpty() ? null : roles.getFirst();
        return new UserView(user.getId(), user.getUsername(), user.getNickname(), user.getRealName(),
                user.getGender(), user.getPhone(), primaryRole, roles, user.getAccountStatus(),
                user.getAuditStatus(), Boolean.TRUE.equals(user.getBlacklist()), user.getInstallerStatus(),
                user.getLastLoginAt(), user.getCreatedAt(), user.getCreatedAt(), user.getUpdatedAt());
    }

    private AuditSnapshot snapshot(UserEntity user, List<String> roles) {
        return new AuditSnapshot(user.getId(), user.getNickname(), user.getRealName(), user.getGender(),
                normalizedRoles(roles), user.getAccountStatus(), Boolean.TRUE.equals(user.getBlacklist()),
                user.getInstallerStatus());
    }

    private AuditSnapshot snapshot(UserView user) {
        return new AuditSnapshot(user.id(), user.nickname(), user.realName(), user.gender(), user.roles(),
                user.accountStatus(), user.blacklist(), user.installerStatus());
    }

    private RoleCode normalizeRole(String raw) {
        try {
            return RoleCode.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException("INVALID_USER_ROLE", "用户角色不合法");
        }
    }

    private String normalizeAccountStatus(String raw) {
        String value = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT);
        if (!ACCOUNT_STATUSES.contains(value)) {
            throw new BusinessException("INVALID_ACCOUNT_STATUS", "账号状态不合法");
        }
        return value;
    }

    private String normalizeGender(String raw) {
        String value = blankToNull(raw);
        if (value == null) return null;
        String normalized = GENDER_ALIASES.getOrDefault(value, value.toUpperCase(Locale.ROOT));
        if (!Set.of("MALE", "FEMALE", "UNKNOWN").contains(normalized)) {
            throw new BusinessException("INVALID_GENDER", "性别编码不合法");
        }
        return normalized;
    }

    private String normalizePhone(String raw) {
        String value = raw == null ? "" : raw.replaceAll("\\s+", "");
        if (value.startsWith("+86")) value = value.substring(3);
        if (!PHONE_PATTERN.matcher(value).matches()) {
            throw new BusinessException("INVALID_PHONE", "手机号格式不合法");
        }
        return value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException notFound() {
        return new BusinessException(404, "USER_NOT_FOUND", "用户不存在");
    }

    private BusinessException phoneAlreadyExists() {
        return new BusinessException(409, "PHONE_ALREADY_EXISTS", "该手机号已存在，请直接编辑已有用户");
    }

    public record CreateCommand(String nickname, String realName, String gender, String phone, String role) { }
    public record UpdateCommand(String nickname, String realName, String gender, String role) { }
    public record AuditContext(String requestId, String clientIp) { }
    public record UserPage(List<UserView> list, long total, int page, int pageSize) { }
    public record UserView(long id, String username, String nickname, String realName, String gender, String phone,
                           String role, List<String> roles, String accountStatus, String auditStatus,
                           boolean blacklist, String installerStatus, LocalDateTime lastLoginAt,
                           LocalDateTime createdAt, LocalDateTime registerTime, LocalDateTime updatedAt) { }
    private record AuditSnapshot(long id, String nickname, String realName, String gender, List<String> roles,
                                 String accountStatus, boolean blacklist, String installerStatus) { }
}
