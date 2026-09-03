package com.lczz.review;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"lczz.file.local-root=target/test-review-storage", "lczz.file.max-bytes=64"})
class OrderReviewIntegrationTests {
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52
    };
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    private long adminId;
    private long installerId;
    private long customerId;
    private long dealerId;
    private long outsiderId;
    private long orderId;
    private String customerToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM work_order_review");
        jdbcTemplate.update("DELETE FROM work_order_progress");
        jdbcTemplate.update("DELETE FROM material_request_item");
        jdbcTemplate.update("DELETE FROM material_request");
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        cleanStorage();
        adminId = createUser("review-admin", "管理员", "13940000001", RoleCode.ADMIN);
        installerId = createUser(null, "张师傅", "13940000002", RoleCode.INSTALLER);
        customerId = createUser(null, "王客户", "13840000001", RoleCode.CUSTOMER);
        dealerId = createUser(null, "经销商客户", "13840000002", RoleCode.DEALER);
        outsiderId = createUser(null, "其他客户", "13840000003", RoleCode.CUSTOMER);
        orderId = createOrder("WO-REVIEW-001", customerId, "PENDING_REVIEW");
        customerToken = token(customerId, RoleCode.CUSTOMER);
    }

    @Test
    void boundCustomerReviewsOnceAndOnlyAdminCanReadContent() throws Exception {
        String imageUrl = uploadImage(customerToken);
        String request = """
                {"orderId":%d,"score":5,"content":"师傅专业，安装完成后运行正常", "liked":true,
                 "label":"超赞","images":["%s"]}
                """.formatted(orderId, imageUrl.replace("&", "\\u0026"));
        mockMvc.perform(post("/api/orders/evaluation").header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json").content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(orderId))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.score").doesNotExist())
                .andExpect(jsonPath("$.data.content").doesNotExist())
                .andExpect(jsonPath("$.data.images").doesNotExist());
        assertThat(orderMapper.selectById(orderId).getOrderStatus()).isEqualTo("REVIEWED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_review WHERE order_id=?",
                Long.class, orderId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? "
                + "AND from_status='PENDING_REVIEW' AND to_status='REVIEWED'", Long.class, orderId)).isEqualTo(1L);
        long reviewFileId = jdbcTemplate.queryForObject("SELECT file_id FROM business_file_relation "
                + "WHERE business_type='REVIEW'", Long.class);

        mockMvc.perform(get("/api/files/" + reviewFileId + "/url")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/files/" + reviewFileId + "/url")
                        .header("Authorization", "Bearer " + token(installerId, RoleCode.INSTALLER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/files/" + reviewFileId + "/url")
                        .header("Authorization", "Bearer " + token(adminId, RoleCode.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").isNotEmpty());

        mockMvc.perform(get("/api/orders/evaluation/" + orderId)
                        .header("Authorization", "Bearer " + token(installerId, RoleCode.INSTALLER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("REVIEW_READ_FORBIDDEN"));
        mockMvc.perform(get("/api/orders/evaluation/" + orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("REVIEW_READ_FORBIDDEN"));
        mockMvc.perform(get("/api/orders/evaluation/" + orderId)
                        .header("Authorization", "Bearer " + token(adminId, RoleCode.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("师傅专业，安装完成后运行正常"))
                .andExpect(jsonPath("$.data.images[0]").isNotEmpty());
        mockMvc.perform(get("/api/orders/evaluation/ids")
                        .header("Authorization", "Bearer " + token(adminId, RoleCode.ADMIN)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0]").value(Long.toString(orderId)));
        mockMvc.perform(get("/api/orders/evaluation/ids").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("REVIEW_READ_FORBIDDEN"));

        mockMvc.perform(post("/api/orders/evaluation").header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json").content(request))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDER_ALREADY_REVIEWED"));
    }

    @Test
    void rejectsUnboundUserAndOrdersOutsidePendingReview() throws Exception {
        String body = "{\"orderId\":" + orderId + ",\"score\":4,\"content\":\"总体满意\"}";
        mockMvc.perform(post("/api/orders/evaluation")
                        .header("Authorization", "Bearer " + token(outsiderId, RoleCode.CUSTOMER))
                        .contentType("application/json").content(body))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_BOUND"));
        mockMvc.perform(post("/api/orders/evaluation")
                        .header("Authorization", "Bearer " + token(installerId, RoleCode.INSTALLER))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("REVIEW_SUBMIT_FORBIDDEN"));

        long activeOrder = createOrder("WO-REVIEW-002", customerId, "IN_PROGRESS");
        mockMvc.perform(post("/api/orders/evaluation").header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json")
                        .content("{\"orderId\":" + activeOrder + ",\"score\":5,\"content\":\"提前评价\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDER_NOT_REVIEWABLE"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_review", Long.class)).isZero();
    }

    @Test
    void dealerCanReviewAnOrderBoundToDealerRole() throws Exception {
        long dealerOrder = createOrder("WO-REVIEW-003", dealerId, "PENDING_REVIEW");
        mockMvc.perform(post("/api/orders/evaluation")
                        .header("Authorization", "Bearer " + token(dealerId, RoleCode.DEALER))
                        .contentType("application/json")
                        .content("{\"orderId\":" + dealerOrder + ",\"score\":4,\"content\":\"服务不错\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.orderId").value(dealerOrder));
        assertThat(orderMapper.selectById(dealerOrder).getOrderStatus()).isEqualTo("REVIEWED");
    }

    @Test
    void invalidImageBindingRollsBackReviewAndOrderStatus() throws Exception {
        String outsiderImage = uploadImage(token(outsiderId, RoleCode.CUSTOMER));
        String body = "{\"orderId\":" + orderId + ",\"score\":5,\"content\":\"测试回滚\","
                + "\"images\":[\"" + outsiderImage.replace("&", "\\u0026") + "\"]}";
        mockMvc.perform(post("/api/orders/evaluation").header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FILE_BIND_FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_review", Long.class)).isZero();
        assertThat(orderMapper.selectById(orderId).getOrderStatus()).isEqualTo("PENDING_REVIEW");
    }

    private String uploadImage(String token) throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "review.png", "image/png", PNG);
        String response = mockMvc.perform(multipart("/api/upload/image").file(image)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/url").asText();
    }

    private long createOrder(String number, long customer, String status) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus(status);
        order.setCustomerUserId(customer);
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13840000001");
        order.setInstallerUserId(installerId);
        order.setDetailedAddress("测试地址");
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy(adminId);
        orderMapper.insert(order);
        return order.getId();
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
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?",
                user.getId(), role.name());
        return user.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role))).value();
    }

    private void cleanStorage() throws Exception {
        Path root = Path.of("target/test-review-storage").toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();
        if (!root.startsWith(target) || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(root)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
    }
}
