package com.lczz.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.common.exception.BusinessException;
import com.lczz.user.service.UserManagementService;
import com.lczz.user.service.UserManagementService.AuditContext;
import com.lczz.user.service.UserManagementService.CreateCommand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.datasource.url=jdbc:h2:mem:admin_creation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
class AdminCreationIntegrationTests {
    private static final String PASSWORD = " TestOnly!111 ";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder encoder;
    @Autowired UserManagementService service;
    private long adminId;
    private String adminToken;

    @BeforeEach
    void reset() {
        for (String table : List.of("operation_audit_log", "user_wechat_identity", "sys_user_role", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        adminId = fixture(RoleCode.ADMIN, "13911100001");
        adminToken = jwt.issue(actor(adminId, RoleCode.ADMIN)).value();
    }

    @Test
    void createdAdminCanLoginWithNormalizedUsernameAndAccessAdminApisWithoutCredentialDisclosure() throws Exception {
        for (String prefix : List.of("/api", "/api/v1")) {
            String suffix = prefix.equals("/api") ? "1" : "2";
            Map<String, Object> body = payload("1391110001" + suffix, " Staff." + suffix + " ");
            JsonNode created = data(create(prefix, body).andExpect(status().isOk()));
            long id = created.path("id").asLong();
            assertThat(created.path("username").asText()).isEqualTo("staff." + suffix);
            assertThat(created.path("role").asText()).isEqualTo("ADMIN");
            assertSafe(created);
            UserEntity stored = users.selectById(id);
            String hash = stored.getPasswordHash();
            assertThat(hash).isNotEqualTo(PASSWORD);
            assertThat(encoder.matches(PASSWORD, hash)).isTrue();
            JsonNode login = data(login(prefix, " STAFF." + suffix + " ", PASSWORD).andExpect(status().isOk()));
            String token = login.path("token").asText();
            assertThat(token).isNotBlank();
            assertThat(login.at("/userInfo/roles/0").asText()).isEqualTo("admin");
            assertSafe(login);
            JsonNode list = data(mvc.perform(get(prefix + "/users/list").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()));
            assertSafe(list);
            assertThat(list.toString()).doesNotContain(hash);
            JsonNode detail = data(mvc.perform(get(prefix + "/users/detail/" + id)
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
            assertSafe(detail);
            String audit = jdbc.queryForObject("SELECT after_json FROM operation_audit_log WHERE business_id=?",
                    String.class, Long.toString(id));
            assertThat(audit).contains("ADMIN").doesNotContain(PASSWORD, hash, "password", "passwordHash");
            login(prefix, "staff." + suffix, PASSWORD.trim()).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void loginAndExistingTokensRespectDisableBlacklistAndRoleRemoval() throws Exception {
        long id = data(create("/api", payload("13911100011", "staff.one")).andExpect(status().isOk())).path("id").asLong();
        String token = data(login("/api", "staff.one", PASSWORD).andExpect(status().isOk())).path("token").asText();
        mvc.perform(patch("/api/users/" + id + "/status").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"accountStatus\":\"DISABLED\"}")).andExpect(status().isOk());
        login("/api", "staff.one", PASSWORD).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/list").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/users/" + id + "/status").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"accountStatus\":\"ENABLED\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/users/" + id + "/blacklist").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"blacklist\":true,\"reason\":\"测试黑名单\"}")).andExpect(status().isOk());
        login("/api", "staff.one", PASSWORD).andExpect(status().isForbidden());
        mvc.perform(get("/api/users/list").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/users/" + id + "/blacklist").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"blacklist\":false,\"reason\":\"解除测试\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/users/" + id).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content("{\"nickname\":\"用户\",\"role\":\"CUSTOMER\"}")).andExpect(status().isOk());
        login("/api", "staff.one", PASSWORD).andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("ADMIN_REQUIRED"));
        mvc.perform(get("/api/users/list").header("Authorization", "Bearer " + token)).andExpect(status().isForbidden());
    }

    @Test
    void creationEnforcesRoleAndRejectsCredentialsOnOtherRoles() throws Exception {
        for (String prefix : List.of("/api", "/api/v1")) {
            mvc.perform(post(prefix + "/users").contentType("application/json")
                    .content(json.writeValueAsString(payload("13911100011", "staff.one")))).andExpect(status().isUnauthorized());
            for (RoleCode role : List.of(RoleCode.CUSTOMER, RoleCode.INSTALLER, RoleCode.DEALER)) {
                long id = fixture(role, "1391110" + String.format("%04d", role.ordinal() + (prefix.equals("/api") ? 30 : 40)));
                String token = jwt.issue(actor(id, role)).value();
                mvc.perform(post(prefix + "/users").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(json.writeValueAsString(payload("13911100011", "staff.one"))))
                        .andExpect(status().isForbidden());
                assertThatThrownBy(() -> service.create(actor(id, role), command("staff.one", "13911100011"), new AuditContext("forbidden", "127.0.0.1")))
                        .isInstanceOf(BusinessException.class).extracting("code").isEqualTo("FORBIDDEN");
            }
        }
        for (RoleCode role : List.of(RoleCode.CUSTOMER, RoleCode.INSTALLER, RoleCode.DEALER)) {
            Map<String, Object> body = payload("13911100011", "staff.one");
            body.put("role", role.name());
            for (String field : List.of("username", "password", "confirmPassword")) {
                Map<String, Object> onlyOne = new LinkedHashMap<>(body);
                for (String credential : List.of("username", "password", "confirmPassword")) if (!credential.equals(field)) onlyOne.remove(credential);
                create("/api", onlyOne).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("ADMIN_CREDENTIALS_NOT_ALLOWED"));
            }
        }
        assertThat(count("operation_audit_log")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE username='staff.one'", Integer.class)).isZero();
    }

    @Test
    void invalidCredentialsDoNotCreateUsersRolesOrAuditRecords() throws Exception {
        int initialUsers = count("sys_user");
        int initialRoles = count("sys_user_role");
        for (String field : List.of("username", "password", "confirmPassword")) {
            for (String value : new String[] {null, "", "   "}) {
                Map<String, Object> body = payload("13911100011", "staff.one");
                body.put(field, value);
                create("/api", body).andExpect(status().isBadRequest());
            }
        }
        for (String username : List.of("abc", "u".repeat(65), "staff one", "用户账号", "staff@one")) {
            create("/api", payload("13911100011", username)).andExpect(status().isBadRequest());
        }
        for (String password : List.of("short11", "x".repeat(73), "密".repeat(25))) {
            Map<String, Object> body = payload("13911100011", "staff.one");
            body.put("password", password); body.put("confirmPassword", password);
            create("/api", body).andExpect(status().isBadRequest());
        }
        Map<String, Object> mismatch = payload("13911100011", "staff.one");
        mismatch.put("confirmPassword", "different111");
        create("/api", mismatch).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("PASSWORD_CONFIRM_MISMATCH"));
        assertThat(count("sys_user")).isEqualTo(initialUsers);
        assertThat(count("sys_user_role")).isEqualTo(initialRoles);
        assertThat(count("operation_audit_log")).isZero();
    }

    @Test
    void duplicateNormalizedUsernamePhoneAndReservedUsernameReturnConflictWithoutPartialWrites() throws Exception {
        long id = data(create("/api", payload("13911100011", "staff.one")).andExpect(status().isOk())).path("id").asLong();
        create("/api", payload("13911100012", " STAFF.ONE ")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("USERNAME_ALREADY_EXISTS"));
        create("/api", payload("13911100011", "staff.two")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PHONE_ALREADY_EXISTS"));
        jdbc.update("UPDATE sys_user SET deleted=TRUE WHERE id=?", id);
        create("/api", payload("13911100012", "staff.one")).andExpect(status().isConflict());
        assertThat(count("sys_user")).isEqualTo(2);
        assertThat(count("sys_user_role")).isEqualTo(2);
        assertThat(count("operation_audit_log")).isEqualTo(1);
    }

    @Test
    void acceptsPasswordByteBoundariesAndPreservesCredentialFreeCreation() throws Exception {
        int suffix = 11;
        for (String password : List.of("12345678", "x".repeat(72), "密".repeat(24))) {
            Map<String, Object> body = payload("139111000" + suffix, "staff." + suffix++);
            body.put("password", password); body.put("confirmPassword", password);
            JsonNode created = data(create("/api", body).andExpect(status().isOk()));
            login("/api", created.path("username").asText(), password).andExpect(status().isOk());
        }
        for (RoleCode role : List.of(RoleCode.CUSTOMER, RoleCode.INSTALLER, RoleCode.DEALER)) {
            Map<String, Object> body = payload("139111000" + suffix, "unused." + suffix++);
            body.put("role", role.name());
            List.of("username", "password", "confirmPassword").forEach(body::remove);
            JsonNode created = data(create("/api", body).andExpect(status().isOk()));
            UserEntity stored = users.selectById(created.path("id").asLong());
            assertThat(stored.getUsername()).isNull();
            assertThat(stored.getPasswordHash()).isNull();
            assertThat(created.path("role").asText()).isEqualTo(role.name());
        }
    }

    @Test
    void concurrentCreatesWithSameUsernameProduceOneCompleteAccount() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var first = pool.submit(() -> concurrentCreate(start, "13911100011"));
            var second = pool.submit(() -> concurrentCreate(start, "13911100012"));
            start.countDown();
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(count("sys_user")).isEqualTo(2);
        assertThat(count("sys_user_role")).isEqualTo(2);
        assertThat(count("operation_audit_log")).isEqualTo(1);
        login("/api", "staff.race", PASSWORD).andExpect(status().isOk());
    }

    private int concurrentCreate(CountDownLatch start, String phone) throws InterruptedException {
        assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
        try {
            service.create(actor(adminId, RoleCode.ADMIN), command("staff.race", phone), new AuditContext("race", "127.0.0.1"));
            return 200;
        } catch (BusinessException exception) { return exception.getStatus(); }
    }

    private CreateCommand command(String username, String phone) {
        return new CreateCommand("新增管理员", null, null, phone, "ADMIN", username, PASSWORD, PASSWORD);
    }

    private Map<String, Object> payload(String phone, String username) {
        return new LinkedHashMap<>(Map.of("nickname", "新增管理员", "phone", phone, "role", "ADMIN",
                "username", username, "password", PASSWORD, "confirmPassword", PASSWORD));
    }

    private ResultActions create(String prefix, Map<String, Object> body) throws Exception {
        return mvc.perform(post(prefix + "/users").header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(json.writeValueAsString(body)));
    }

    private ResultActions login(String prefix, String username, String password) throws Exception {
        return mvc.perform(post(prefix + "/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private JsonNode data(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).path("data");
    }

    private void assertSafe(JsonNode response) {
        assertThat(response.toString()).doesNotContain(PASSWORD, "password", "passwordHash", "confirmPassword");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private long fixture(RoleCode role, String phone) {
        UserEntity user = new UserEntity();
        user.setNickname(role.name()); user.setPhone(phone);
        user.setAccountStatus("ENABLED"); user.setAuditStatus("APPROVED");
        user.setBlacklist(false); user.setDeleted(false); users.insert(user);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?", user.getId(), role.name());
        return user.getId();
    }

    private AuthenticatedUser actor(long id, RoleCode role) {
        return new AuthenticatedUser(id, null, "测试用户", null, Set.of(role));
    }
}
