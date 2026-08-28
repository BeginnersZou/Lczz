package com.lczz.file;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "lczz.file.local-root=target/test-file-storage",
        "lczz.file.max-bytes=64",
        "lczz.file.max-image-bytes=32"
})
class FileIntegrationTests {
    private static final byte[] PNG = new byte[] {
            (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0x00, 0x00, 0x00, 0x0d, 0x49, 0x48, 0x44, 0x52
    };
    private static final byte[] MP4 = new byte[] {
            0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6f, 0x6d, 0x00, 0x00, 0x00, 0x00,
            0x69, 0x73, 0x6f, 0x6d, 0x6d, 0x70, 0x34, 0x32
    };
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    private long installerId;
    private long customerId;
    private long outsiderId;
    private long orderId;
    private String installerToken;
    private String customerToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM material_request_item");
        jdbcTemplate.update("DELETE FROM material_request");
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        cleanStorage();
        long adminId = createUser("file-admin", "管理员", "13920000001", RoleCode.ADMIN);
        installerId = createUser(null, "张师傅", "13920000002", RoleCode.INSTALLER);
        customerId = createUser(null, "王客户", "13820000001", RoleCode.CUSTOMER);
        outsiderId = createUser(null, "赵客户", "13820000002", RoleCode.CUSTOMER);
        orderId = createOrder(adminId);
        installerToken = token(installerId, RoleCode.INSTALLER);
        customerToken = token(customerId, RoleCode.CUSTOMER);
    }

    @Test
    void orderFileRequiresBusinessAccessAndSignedUrlWorksWithoutJwt() throws Exception {
        JsonNode uploaded = upload("install.png", "image/png", PNG, installerToken, orderId);
        long fileId = uploaded.path("id").asLong();
        String signedUrl = uploaded.path("url").asText();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE file_id=?",
                Long.class, fileId)).isEqualTo(1L);

        mockMvc.perform(get("/api/orders/list")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].fileList[0].id").value(fileId));

        mockMvc.perform(get("/api/files/" + fileId + "/url")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.url").isNotEmpty());
        mockMvc.perform(get("/api/files/" + fileId + "/url")
                        .header("Authorization", "Bearer " + token(outsiderId, RoleCode.CUSTOMER)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FILE_ACCESS_FORBIDDEN"));
        mockMvc.perform(get(signedUrl)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(PNG));
        mockMvc.perform(get(signedUrl.replace("signature=", "signature=x")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("INVALID_FILE_SIGNATURE"));
    }

    @Test
    void acceptsRealMp4ProgressMedia() throws Exception {
        byte[] videoBytes = new byte[40];
        System.arraycopy(MP4, 0, videoBytes, 0, MP4.length);
        MockMultipartFile video = new MockMultipartFile("file", "progress.mp4", "video/mp4", videoBytes);
        mockMvc.perform(multipart("/api/orders/upload").file(video)
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mimeType").value("video/mp4"))
                .andExpect(jsonPath("$.data.size").value(40))
                .andExpect(jsonPath("$.data.url").isNotEmpty());
    }

    @Test
    void rejectsSpoofedTypeExtensionAndOversize() throws Exception {
        MockMultipartFile spoofed = new MockMultipartFile("file", "fake.png", "image/png", "not-image".getBytes());
        mockMvc.perform(multipart("/api/files/upload").file(spoofed)
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_FILE_TYPE"));
        MockMultipartFile mismatch = new MockMultipartFile("file", "photo.jpg", "image/jpeg", PNG);
        mockMvc.perform(multipart("/api/files/upload").file(mismatch)
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_FILE_TYPE"));
        MockMultipartFile invalidUsage = new MockMultipartFile("file", "install.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/files/upload").file(invalidUsage)
                        .param("businessType", "ORDER").param("businessId", String.valueOf(orderId))
                        .param("usageType", "REVIEW").header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("INVALID_FILE_RELATION"));
        byte[] oversized = new byte[65];
        System.arraycopy(PNG, 0, oversized, 0, PNG.length);
        MockMultipartFile tooLarge = new MockMultipartFile("file", "large.png", "image/png", oversized);
        mockMvc.perform(multipart("/api/files/upload").file(tooLarge)
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_asset", Long.class)).isZero();
    }

    @Test
    void unauthorizedBusinessUploadLeavesNoMetadataRelationOrStoredObject() throws Exception {
        long inaccessibleOrder = createOtherOrder();
        MockMultipartFile image = new MockMultipartFile("file", "install.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/files/upload").file(image)
                        .param("businessType", "ORDER").param("businessId", String.valueOf(inaccessibleOrder))
                        .param("usageType", "ATTACHMENT")
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FILE_RELATION_FORBIDDEN"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_asset", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation", Long.class)).isZero();
        assertThat(storedFileCount()).isZero();
    }

    @Test
    void orderDetailReturnsAttachmentsAndOnlyWriterCanUnbind() throws Exception {
        JsonNode uploaded = upload("order.png", "image/png", PNG, installerToken, orderId);
        long fileId = uploaded.path("id").asLong();

        mockMvc.perform(get("/api/orders/detail/{id}", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileList[0].id").value(fileId))
                .andExpect(jsonPath("$.data.fileList[0].url").isNotEmpty());
        mockMvc.perform(delete("/api/v1/files/{id}/relations", fileId)
                        .param("businessType", "ORDER").param("businessId", String.valueOf(orderId))
                        .param("usageType", "ATTACHMENT")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FILE_RELATION_FORBIDDEN"));
        mockMvc.perform(delete("/api/v1/files/{id}/relations", fileId)
                        .param("businessType", "ORDER").param("businessId", String.valueOf(orderId))
                        .param("usageType", "ATTACHMENT")
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        mockMvc.perform(get("/api/orders/detail/{id}", orderId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.fileList").isEmpty());
    }

    private JsonNode upload(String name, String mime, byte[] bytes, String token, long targetOrderId) throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", name, mime, bytes);
        String response = mockMvc.perform(multipart("/api/files/upload").file(image)
                        .param("businessType", "ORDER").param("businessId", String.valueOf(targetOrderId))
                        .param("usageType", "ATTACHMENT").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sha256").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
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

    private long createOrder(long adminId) { return insertOrder("WO-FILE-001", installerId, customerId, adminId); }
    private long createOtherOrder() { return insertOrder("WO-FILE-002", outsiderId, outsiderId, outsiderId); }

    private long insertOrder(String number, long installer, long customer, long creator) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus("IN_PROGRESS");
        order.setCustomerUserId(customer);
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13820000001");
        order.setInstallerUserId(installer);
        order.setDetailedAddress("测试地址");
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy(creator);
        orderMapper.insert(order);
        return order.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role))).value();
    }

    private long storedFileCount() throws Exception {
        Path root = Path.of("target/test-file-storage");
        if (!Files.exists(root)) return 0;
        try (var paths = Files.walk(root)) { return paths.filter(Files::isRegularFile).count(); }
    }

    private void cleanStorage() throws Exception {
        Path root = Path.of("target/test-file-storage").toAbsolutePath().normalize();
        Path target = Path.of("target").toAbsolutePath().normalize();
        if (!root.startsWith(target) || !Files.exists(root)) return;
        try (var paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).filter(path -> !path.equals(root)).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (Exception exception) { throw new RuntimeException(exception); }
            });
        }
    }
}
