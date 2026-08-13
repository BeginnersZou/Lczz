package com.lczz.progress;

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
@TestPropertySource(properties = {"lczz.file.local-root=target/test-progress-storage", "lczz.file.max-bytes=64"})
class WorkProgressIntegrationTests {
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
    private long otherInstallerId;
    private long customerId;
    private long otherCustomerId;
    private long orderId;
    private String installerToken;
    private String customerToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM file_asset");
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
        adminId = createUser("progress-admin", "管理员", "13930000001", RoleCode.ADMIN);
        installerId = createUser(null, "张师傅", "13930000002", RoleCode.INSTALLER);
        otherInstallerId = createUser(null, "李师傅", "13930000003", RoleCode.INSTALLER);
        customerId = createUser(null, "王客户", "13830000001", RoleCode.CUSTOMER);
        otherCustomerId = createUser(null, "赵客户", "13830000002", RoleCode.CUSTOMER);
        orderId = createOrder();
        installerToken = token(installerId, RoleCode.INSTALLER);
        customerToken = token(customerId, RoleCode.CUSTOMER);
    }

    @Test
    void installerCanSubmitMultipleProgressAndCustomerReadsChronologically() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json")
                        .content("{\"description\":\"第一天完成管线铺设\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("PROGRESS"));
        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json")
                        .content("{\"description\":\"第二天完成室外机安装\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].description").value("第一天完成管线铺设"))
                .andExpect(jsonPath("$.data[1].description").value("第二天完成室外机安装"));
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId))
                .isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? "
                + "AND to_status='IN_PROGRESS'", Long.class, orderId)).isEqualTo(1L);
    }

    @Test
    void completionRequiresImageTransitionsOnceAndReturnsSignedImages() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/completion")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json")
                        .content("{\"description\":\"安装完成并试机正常\",\"fileIds\":[]}"))
                .andExpect(status().isBadRequest());
        long fileId = uploadImage();
        String body = "{\"description\":\"安装完成并试机正常\",\"fileIds\":[" + fileId + "]}";
        mockMvc.perform(post("/api/orders/" + orderId + "/completion")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("COMPLETION"))
                .andExpect(jsonPath("$.data.images[0].url").isNotEmpty());
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId))
                .isEqualTo("PENDING_REVIEW");
        mockMvc.perform(post("/api/orders/" + orderId + "/completion")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDER_NOT_COMPLETABLE"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress WHERE order_id=? "
                + "AND progress_type='COMPLETION'", Long.class, orderId)).isEqualTo(1L);

        mockMvc.perform(get("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].images[0].url").isNotEmpty());
    }

    @Test
    void unrelatedUsersCannotSubmitOrReadProgress() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + token(otherInstallerId, RoleCode.INSTALLER))
                        .contentType("application/json").content("{\"description\":\"越权提交\"}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_ASSIGNED"));
        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json").content("{\"description\":\"客户提交\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("PROGRESS_SUBMIT_FORBIDDEN"));
        mockMvc.perform(get("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + token(otherCustomerId, RoleCode.CUSTOMER)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Long.class)).isZero();
    }

    private long uploadImage() throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", "completion.png", "image/png", PNG);
        String response = mockMvc.perform(multipart("/api/files/upload").file(image)
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createOrder() {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo("WO-PROGRESS-001");
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus("PENDING_VISIT");
        order.setCustomerUserId(customerId);
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13830000001");
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
        Path root = Path.of("target/test-progress-storage").toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();
        if (!root.startsWith(target) || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(root)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
    }
}
