package com.lczz.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.auth.wechat.WechatIdentity;
import com.lczz.auth.wechat.WechatIdentityGateway;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrderIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired UserMapper userMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean WechatIdentityGateway wechatGateway;

    private long adminId;
    private long installerId;
    private long otherInstallerId;
    private long customerId;
    private long otherCustomerId;
    private String adminToken;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        adminId = createUser("admin-order", "管理员", "13900000001", RoleCode.ADMIN);
        installerId = createUser(null, "张师傅", "13900000002", RoleCode.INSTALLER);
        otherInstallerId = createUser(null, "李师傅", "13900000003", RoleCode.INSTALLER);
        customerId = createUser(null, "王客户", "13800138000", RoleCode.CUSTOMER);
        otherCustomerId = createUser(null, "赵客户", "13800138001", RoleCode.CUSTOMER);
        adminToken = token(adminId, RoleCode.ADMIN);
    }

    @Test
    void adminCreatesUniqueOrdersAndEveryRoleOnlySeesItsOwnScope() throws Exception {
        JsonNode first = createOrder("13800138000", installerId);
        JsonNode second = createOrder("13800138000", installerId);
        assertThat(first.path("orderNo").asText()).isNotEqualTo(second.path("orderNo").asText());
        assertThat(first.path("customerUserId").asLong()).isEqualTo(customerId);
        assertThat(first.path("selectedMasterList").size()).isEqualTo(1);

        assertListTotal(adminToken, 2);
        assertListTotal(token(customerId, RoleCode.CUSTOMER), 2);
        assertListTotal(token(installerId, RoleCode.INSTALLER), 2);
        assertListTotal(token(otherCustomerId, RoleCode.CUSTOMER), 0);
        assertListTotal(token(otherInstallerId, RoleCode.INSTALLER), 0);

        mockMvc.perform(get("/api/orders/detail/" + first.path("id").asLong())
                        .header("Authorization", "Bearer " + token(otherCustomerId, RoleCode.CUSTOMER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
    }

    @Test
    void unregisteredCustomerGetsHistoricalOrdersAfterBindingPhone() throws Exception {
        JsonNode order = createOrder("13700137000", installerId);
        assertThat(order.path("customerUserId").isMissingNode() || order.path("customerUserId").isNull()).isTrue();
        when(wechatGateway.exchangeLoginCode("order-login-code"))
                .thenReturn(new WechatIdentity("wx-app", "order-open-id", "order-union-id"));
        when(wechatGateway.exchangePhoneCode("order-phone-code")).thenReturn("13700137000");

        mockMvc.perform(post("/api/auth/wechat/login").contentType("application/json")
                        .content("{\"code\":\"order-login-code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needPhone").value(true));
        String response = mockMvc.perform(post("/api/auth/wechat/bind-phone").contentType("application/json")
                        .content("{\"code\":\"order-login-code\",\"phoneCode\":\"order-phone-code\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String customerToken = objectMapper.readTree(response).at("/data/token").asText();

        Long boundUserId = jdbcTemplate.queryForObject(
                "SELECT customer_user_id FROM work_order WHERE id = ?", Long.class, order.path("id").asLong());
        assertThat(boundUserId).isNotNull();
        assertListTotal(customerToken, 1);
    }

    @Test
    void rejectsMultipleInstallersUnauthorizedWritesAndIllegalStatusTransitions() throws Exception {
        String invalidOrder = orderJson("13800138000", "[" + installerId + "," + otherInstallerId + "]");
        mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(invalidOrder))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));

        JsonNode order = createOrder("13800138000", installerId);
        long orderId = order.path("id").asLong();
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS_TRANSITION"));
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("处理中"));
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER))
                        .contentType("application/json").content("{\"status\":\"CANCELLED\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM work_order_status_history WHERE order_id = ?", Long.class, orderId))
                .isEqualTo(2L);
    }

    @Test
    void masterListShowsAllUnfinishedAssignedTasks() throws Exception {
        JsonNode pendingVisit = createOrder("13800138000", installerId);
        JsonNode inProgress = createOrder("13800138000", installerId);
        updateStatus(inProgress.path("id").asLong(), "IN_PROGRESS");

        JsonNode pendingReview = createOrder("13800138000", installerId);
        updateStatus(pendingReview.path("id").asLong(), "IN_PROGRESS");
        updateStatus(pendingReview.path("id").asLong(), "PENDING_REVIEW");

        JsonNode cancelled = createOrder("13800138000", installerId);
        updateStatus(cancelled.path("id").asLong(), "CANCELLED");

        String response = mockMvc.perform(get("/api/orders/masters")
                        .param("keyword", "13900000002")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].unfinishedOrderCount").value(3))
                .andReturn().getResponse().getContentAsString();

        JsonNode unfinishedOrders = objectMapper.readTree(response).at("/data/0/unfinishedOrders");
        assertThat(unfinishedOrders.toString()).contains(
                        pendingVisit.path("orderNo").asText(),
                        inProgress.path("orderNo").asText(),
                        pendingReview.path("orderNo").asText())
                .doesNotContain(cancelled.path("orderNo").asText());
    }

    private JsonNode createOrder(String phone, long masterId) throws Exception {
        String response = mockMvc.perform(post("/api/orders").header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(orderJson(phone, "[" + masterId + "]")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderNo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private void updateStatus(long orderId, String statusCode) throws Exception {
        mockMvc.perform(patch("/api/orders/" + orderId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"status\":\"" + statusCode + "\"}"))
                .andExpect(status().isOk());
    }

    private String orderJson(String phone, String masterIds) {
        return """
                {
                  "taskType":"空调安装",
                  "description":"客厅挂机安装",
                  "customerName":"测试客户",
                  "customerPhone":"%s",
                  "addressArea":["安徽省","合肥市","蜀山区"],
                  "addressDetail":"创新大道100号",
                  "orderStartTime":"2026-08-20T01:00:00Z",
                  "orderEndTime":"2026-08-20T03:00:00Z",
                  "masterIds":%s
                }
                """.formatted(phone, masterIds);
    }

    private void assertListTotal(String token, int total) throws Exception {
        mockMvc.perform(get("/api/orders/list").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(total));
    }

    private long createUser(String username, String name, String phone, RoleCode role) {
        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setRealName(name);
        user.setPhone(phone);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) "
                + "SELECT ?, id FROM sys_role WHERE role_code = ?", user.getId(), role.name());
        return user.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role))).value();
    }
}
