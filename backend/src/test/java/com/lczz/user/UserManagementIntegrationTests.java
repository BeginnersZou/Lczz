package com.lczz.user;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.common.exception.BusinessException;
import com.lczz.user.service.UserManagementService;
import com.lczz.user.service.UserManagementService.AuditContext;
import com.lczz.user.service.UserManagementService.UpdateCommand;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserManagementIntegrationTests {
    private static final String ORIGINAL_PASSWORD = "OldPass@2026";
    private static final String NEW_PASSWORD = "NewPass@2026";
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;
    @Autowired JwtService jwtService;
    @Autowired UserManagementService service;
    @Autowired PasswordEncoder passwordEncoder;

    private long adminId;
    private long customerId;
    private long installerId;
    private long dealerId;
    private String adminToken;

    @BeforeEach
    void resetData() {
        clearBusinessData();
        adminId = createUser("users-admin", "系统管理员", "13960000001", RoleCode.ADMIN);
        customerId = createUser(null, "普通客户", "13860000001", RoleCode.CUSTOMER);
        installerId = createUser(null, "安装师傅", "13860000002", RoleCode.INSTALLER);
        dealerId = createUser(null, "经销商客户", "13860000003", RoleCode.DEALER);
        adminToken = token(adminId, RoleCode.ADMIN);
    }

    @AfterEach
    void removeAuditRows() {
        jdbcTemplate.update("DELETE FROM operation_audit_log");
    }

    @Test
    void adminCanListFilterAndReadSafeUserDetails() throws Exception {
        mockMvc.perform(get("/api/v1/users/list")
                        .param("role", "dealer")
                        .param("keyword", "经销商")
                        .param("accountStatus", "enabled")
                        .param("blacklist", "false")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(dealerId))
                .andExpect(jsonPath("$.data.list[0].role").value("DEALER"))
                .andExpect(jsonPath("$.data.list[0].roles[0]").value("DEALER"))
                .andExpect(jsonPath("$.data.list[0].registerTime").exists())
                .andExpect(jsonPath("$.data.list[0].passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.list[0].lastLoginIp").doesNotExist());

        mockMvc.perform(get("/api/users/detail/{id}", installerId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phone").value("13860000002"))
                .andExpect(jsonPath("$.data.role").value("INSTALLER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ENABLED"));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/v1/users']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/list']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/users/{id}/status']").exists());
    }

    @Test
    void adminCanPreCreateAssignableInstallerAndDuplicatePhoneIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Request-Id", "user-create-request")
                        .contentType("application/json")
                        .content("""
                                {"nickname":"李师傅","realName":"李安装","gender":"male",
                                 "phone":"13860000009","role":"installer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("李师傅"))
                .andExpect(jsonPath("$.data.realName").value("李安装"))
                .andExpect(jsonPath("$.data.gender").value("MALE"))
                .andExpect(jsonPath("$.data.phone").value("13860000009"))
                .andExpect(jsonPath("$.data.role").value("INSTALLER"))
                .andExpect(jsonPath("$.data.accountStatus").value("ENABLED"))
                .andExpect(jsonPath("$.data.auditStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.blacklist").value(false));

        mockMvc.perform(get("/api/v1/orders/masters")
                        .param("keyword", "13860000009")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].masterName").value("李安装"))
                .andExpect(jsonPath("$.data[0].masterPhone").value("13860000009"));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"nickname":"重复用户","realName":"重复用户","gender":"unknown",
                                 "phone":"13860000009","role":"customer"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PHONE_ALREADY_EXISTS"));

        mockMvc.perform(post("/api/users")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER))
                        .contentType("application/json")
                        .content("""
                                {"nickname":"越权用户","phone":"13860000010","role":"installer"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone='13860000009'",
                Integer.class)).isEqualTo(1);
        String auditJson = jdbcTemplate.queryForObject("SELECT after_json FROM operation_audit_log "
                + "WHERE request_id='user-create-request'", String.class);
        assertThat(auditJson).contains("INSTALLER").doesNotContain("13860000009");
    }

    @Test
    void updatesRoleStatusAndBlacklistWithAuditTrail() throws Exception {
        mockMvc.perform(put("/api/v1/users/{id}", customerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Request-Id", "user-update-request")
                        .contentType("application/json")
                        .content("""
                                {"nickname":"新昵称","realName":"新姓名","gender":"female","role":"installer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"))
                .andExpect(jsonPath("$.data.role").value("INSTALLER"))
                .andExpect(jsonPath("$.data.roles.length()").value(1));

        mockMvc.perform(patch("/api/users/{id}/status", customerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"accountStatus\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountStatus").value("DISABLED"));

        mockMvc.perform(post("/api/v1/users/{id}/blacklist", customerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"blacklist\":true,\"reason\":\"多次提交异常请求\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.blacklist").value(true));

        Integer roleCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id = ?", Integer.class, customerId);
        assertThat(roleCount).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT r.role_code FROM sys_user_role ur "
                + "JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=?", String.class, customerId))
                .isEqualTo("INSTALLER");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE business_id=?",
                Integer.class, Long.toString(customerId))).isEqualTo(3);
        String updateAudit = jdbcTemplate.queryForObject("SELECT after_json FROM operation_audit_log "
                + "WHERE request_id='user-update-request'", String.class);
        assertThat(updateAudit).contains("INSTALLER").doesNotContain("13860000001");
    }

    @Test
    void permissionsAndSafetyRulesPreventAdministrativeLockout() throws Exception {
        mockMvc.perform(get("/api/users/list")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));

        mockMvc.perform(put("/api/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"nickname":"管理员","realName":"管理员","gender":"unknown","role":"customer"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_ROLE_CHANGE_FORBIDDEN"));

        mockMvc.perform(patch("/api/users/{id}/status", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"accountStatus\":\"DISABLED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_STATUS_CHANGE_FORBIDDEN"));

        mockMvc.perform(post("/api/users/{id}/blacklist", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"blacklist\":true,\"reason\":\"错误操作测试\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("SELF_BLACKLIST_FORBIDDEN"));

        AuthenticatedUser emergencyOperator = actor(999_999L, RoleCode.ADMIN);
        assertThatThrownBy(() -> service.changeStatus(emergencyOperator, adminId, "DISABLED",
                new AuditContext("last-admin-test", "127.0.0.1")))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo("LAST_ADMIN_REQUIRED");

        mockMvc.perform(get("/api/users/list").param("role", "unknown")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_USER_ROLE"));

        mockMvc.perform(patch("/api/users/{id}/status", customerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("{\"accountStatus\":\"FROZEN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_ACCOUNT_STATUS"));

        mockMvc.perform(get("/api/users/detail/{id}", 999_999L)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("USER_NOT_FOUND"));
    }

    @Test
    void onlyAdminCanChangeOwnPasswordWithOriginalPassword() throws Exception {
        String base = """
                {"nickname":"系统管理员","realName":"系统管理员","gender":"UNKNOWN","role":"ADMIN",
                 "originalPassword":"%s","newPassword":"%s","confirmPassword":"%s"}
                """;

        mockMvc.perform(put("/api/v1/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(base.formatted(ORIGINAL_PASSWORD, NEW_PASSWORD, "Different@2026")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("PASSWORD_CONFIRM_MISMATCH"));

        mockMvc.perform(put("/api/v1/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content(base.formatted("WrongPass@2026", NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ORIGINAL_PASSWORD_INCORRECT"));

        mockMvc.perform(put("/api/v1/users/{id}", customerId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"nickname":"普通客户","realName":"普通客户","gender":"UNKNOWN","role":"CUSTOMER",
                                 "originalPassword":"OldPass@2026","newPassword":"OtherPass@2026",
                                 "confirmPassword":"OtherPass@2026"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("PASSWORD_CHANGE_FORBIDDEN"));

        mockMvc.perform(put("/api/v1/users/{id}", adminId)
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Request-Id", "password-change-request")
                        .contentType("application/json")
                        .content(base.formatted(ORIGINAL_PASSWORD, NEW_PASSWORD, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(adminId))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String updatedHash = userMapper.selectById(adminId).getPasswordHash();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, updatedHash)).isTrue();
        assertThat(passwordEncoder.matches(ORIGINAL_PASSWORD, updatedHash)).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM operation_audit_log "
                + "WHERE request_id='password-change-request' AND operation_type='ADMIN_PASSWORD_CHANGE'",
                Integer.class)).isEqualTo(1);
        String auditAfter = jdbcTemplate.queryForObject("SELECT after_json FROM operation_audit_log "
                + "WHERE request_id='password-change-request' AND operation_type='ADMIN_PASSWORD_CHANGE'",
                String.class);
        assertThat(auditAfter).contains("passwordChanged").doesNotContain(ORIGINAL_PASSWORD)
                .doesNotContain(NEW_PASSWORD);
    }

    @Test
    void concurrentRoleChangesCannotRemoveEveryActiveAdmin() throws Exception {
        long secondAdminId = createUser("users-admin-two", "第二管理员", "13960000002", RoleCode.ADMIN);
        AuthenticatedUser firstActor = actor(adminId, RoleCode.ADMIN);
        AuthenticatedUser secondActor = actor(secondAdminId, RoleCode.ADMIN);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> first = executor.submit(() -> changeOtherAdminRole(start, firstActor, secondAdminId));
            Future<String> second = executor.submit(() -> changeOtherAdminRole(start, secondActor, adminId));
            start.countDown();
            List<String> results = List.of(first.get(), second.get());
            assertThat(results).contains("SUCCESS", "LAST_ADMIN_REQUIRED");
        }
        Integer activeAdmins = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM sys_user u
                JOIN sys_user_role ur ON ur.user_id=u.id
                JOIN sys_role r ON r.id=ur.role_id
                WHERE r.role_code='ADMIN' AND u.account_status='ENABLED'
                  AND u.audit_status='APPROVED' AND u.blacklist=FALSE AND u.deleted=FALSE
                """, Integer.class);
        assertThat(activeAdmins).isEqualTo(1);
    }

    private String changeOtherAdminRole(CountDownLatch start, AuthenticatedUser actor, long targetId)
            throws InterruptedException {
        start.await();
        try {
            service.update(actor, targetId,
                    new UpdateCommand("并发测试用户", "并发测试用户", "UNKNOWN", "CUSTOMER"),
                    new AuditContext("concurrent-" + actor.userId(), "127.0.0.1"));
            return "SUCCESS";
        } catch (BusinessException exception) {
            return exception.getCode();
        }
    }

    private void clearBusinessData() {
        jdbcTemplate.update("DELETE FROM operation_audit_log");
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM work_order_review");
        jdbcTemplate.update("DELETE FROM work_order_progress");
        jdbcTemplate.update("DELETE FROM material_request_item");
        jdbcTemplate.update("DELETE FROM material_request");
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    private long createUser(String username, String name, String phone, RoleCode role) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setNickname(name);
        user.setRealName(name);
        user.setGender("UNKNOWN");
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(ORIGINAL_PASSWORD));
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?",
                user.getId(), role.name());
        return user.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(actor(userId, role)).value();
    }

    private AuthenticatedUser actor(long userId, RoleCode role) {
        return new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role));
    }
}
