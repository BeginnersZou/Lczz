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
import com.lczz.progress.service.WorkProgressService;
import com.lczz.progress.service.ProgressConfirmationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired WorkProgressService progressService;
    @Autowired ProgressConfirmationService confirmationService;
    @MockitoBean RestClient restClient;

    private long adminId;
    private long installerId;
    private long otherInstallerId;
    private long customerId;
    private long otherCustomerId;
    private long orderId;
    private String adminToken;
    private String installerToken;
    private String customerToken;

    @BeforeEach
    void resetData() throws Exception {
        jdbcTemplate.update("DELETE FROM sms_notification");
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
        adminToken = token(adminId, RoleCode.ADMIN);
        installerToken = token(installerId, RoleCode.INSTALLER);
        customerToken = token(customerId, RoleCode.CUSTOMER);
    }

    @Test
    void installerCanSubmitMultipleProgressAndCustomerReadsChronologically() throws Exception {
        String firstResponse = mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json")
                        .content("{\"description\":\"第一天完成管线铺设\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.type").value("PROGRESS"))
                .andReturn().getResponse().getContentAsString();
        String submittedAt = objectMapper.readTree(firstResponse).at("/data/submittedAt").asText();
        assertThat(submittedAt).endsWith("+08:00");
        assertThat(OffsetDateTime.parse(submittedAt).toInstant()).isBeforeOrEqualTo(Instant.now());
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
    void progressBindsAndReturnsAllSelectedFilesInOrder() throws Exception {
        long firstFileId = uploadImage("progress-1.png");
        long secondFileId = uploadImage("progress-2.png");
        long thirdFileId = uploadImage("progress-3.png");
        String body = "{\"description\":\"一次提交三张施工照片\",\"fileIds\":["
                + firstFileId + "," + secondFileId + "," + thirdFileId + "]}";

        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(3))
                .andExpect(jsonPath("$.data.images[0].id").value(firstFileId))
                .andExpect(jsonPath("$.data.images[0].uploadedAt")
                        .value(org.hamcrest.Matchers.endsWith("+08:00")))
                .andExpect(jsonPath("$.data.images[1].id").value(secondFileId))
                .andExpect(jsonPath("$.data.images[2].id").value(thirdFileId));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation "
                + "WHERE business_type='PROGRESS' AND usage_type='PROGRESS'", Long.class)).isEqualTo(3L);
        mockMvc.perform(get("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].images.length()").value(3))
                .andExpect(jsonPath("$.data[0].images[0].id").value(firstFileId))
                .andExpect(jsonPath("$.data[0].images[2].id").value(thirdFileId));
    }

    @Test
    void thirtyPhotosSubmitReadBackAndAdminAppendRespectsTheSameLimit() throws Exception {
        List<Long> ids = uploadImages(30);
        JsonNode created = submitFiles(ids, 200);
        long progressId = created.at("/data/id").asLong();
        assertThat(created.at("/data/images").size()).isEqualTo(30);
        for (int index = 0; index < 30; index++) {
            assertThat(created.at("/data/images/" + index + "/id").asLong()).isEqualTo(ids.get(index));
        }
        for (String caller : new String[] {installerToken, customerToken, adminToken}) {
            mockMvc.perform(get("/api/v1/orders/" + orderId + "/progress")
                            .header("Authorization", "Bearer " + caller))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].images.length()").value(30));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='PROGRESS_SUBMITTED'", Integer.class)).isEqualTo(1);
        confirm(progressId, created.at("/data/contentVersion").asText(), customerToken);
        assertThat(progressList().get(0).path("customerConfirmed").asBoolean()).isTrue();
        jdbcTemplate.update("UPDATE work_order SET order_status='REVIEWED' WHERE id=?", orderId);
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "extra.png", "image/png", PNG))
                        .param("businessType", "PROGRESS").param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").value("FILE_COUNT_LIMIT"))
                .andExpect(jsonPath("$.message").value("每条施工进度最多30张图片"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE business_id=?",
                Long.class, progressId)).isEqualTo(30L);
        mockMvc.perform(delete("/api/v1/files/" + ids.getFirst() + "/relations")
                        .param("businessType", "PROGRESS").param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "replacement.png", "image/png", PNG))
                        .param("businessType", "PROGRESS").param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].images.length()").value(30));
        assertThat(progressList().get(0).path("confirmationStatus").asText()).isEqualTo("ADMIN_EDITED");
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId)).isEqualTo("REVIEWED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='PROGRESS_SUBMITTED'", Integer.class)).isEqualTo(1);
    }

    @Test
    void thirtyFirstPhotoRejectsAndRollsBackTheEntireProgressSubmission() throws Exception {
        JsonNode response = submitFiles(uploadImages(31), 400);
        assertThat(response.path("error").asText()).isEqualTo("FILE_COUNT_LIMIT");
        assertThat(response.path("message").asText()).isEqualTo("每条施工进度最多30张图片");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation", Long.class)).isZero();
        assertThat(orderMapper.selectById(orderId).getOrderStatus()).isEqualTo("PENDING_VISIT");
    }

    @Test
    void videoLimitRemainsNineAndIsIndependentOfThirtyPhotos() throws Exception {
        List<Long> videos = new ArrayList<>();
        byte[] mp4 = new byte[] {0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70,
                0x69, 0x73, 0x6f, 0x6d, 0, 0, 0, 0, 0x69, 0x73, 0x6f, 0x6d, 0x6d, 0x70, 0x34, 0x32};
        for (int index = 0; index < 10; index++) {
            String response = mockMvc.perform(multipart("/api/v1/files/upload")
                            .file(new MockMultipartFile("file", "video-" + index + ".mp4", "video/mp4", mp4))
                            .header("Authorization", "Bearer " + installerToken))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            videos.add(objectMapper.readTree(response).at("/data/id").asLong());
        }
        JsonNode rejected = submitFiles(videos, 400);
        assertThat(rejected.path("error").asText()).isEqualTo("FILE_COUNT_LIMIT");
        assertThat(rejected.path("message").asText()).isEqualTo("每条施工进度最多9个视频");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Long.class)).isZero();
        List<Long> mixed = uploadImages(30);
        mixed.addAll(videos.subList(0, 9));
        JsonNode accepted = submitFiles(mixed, 200);
        assertThat(accepted.at("/data/images").size()).isEqualTo(39);
    }

    @Test
    void concurrentAppendsCannotBothClaimTheThirtiethPhotoSlot() throws Exception {
        long progressId = submitFiles(uploadImages(29), 200).at("/data/id").asLong();
        long first = uploadImage("concurrent-first.png"), second = uploadImage("concurrent-second.png");
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> appendAfterStart(start, progressId, first));
            var two = pool.submit(() -> appendAfterStart(start, progressId, second));
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 400);
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE business_id=?",
                Long.class, progressId)).isEqualTo(30L);
    }

    private int appendAfterStart(CountDownLatch start, long progressId, long fileId) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return mockMvc.perform(post("/api/v1/files/" + fileId + "/relations")
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json")
                        .content("{\"businessType\":\"PROGRESS\",\"businessId\":" + progressId + ",\"usageType\":\"PROGRESS\"}"))
                .andReturn().getResponse().getStatus();
    }

    private List<Long> uploadImages(int count) throws Exception {
        List<Long> result = new ArrayList<>();
        for (int index = 0; index < count; index++) result.add(uploadImage("site-" + index + ".png"));
        return result;
    }

    private JsonNode submitFiles(List<Long> ids, int expected) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("description", "施工附件数量边界", "fileIds", ids));
        return objectMapper.readTree(mockMvc.perform(post("/api/v1/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json").content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    @Test
    void miniProgramAndAdminReturnTheSameZonedProgressWithoutChangingStoredUtc() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken).contentType("application/json")
                        .content("{\"description\":\"凌晨施工跨日记录\"}"))
                .andExpect(status().isOk());
        jdbcTemplate.update("UPDATE work_order_progress SET submitted_at='2026-10-11 16:15:00' WHERE order_id=?", orderId);
        jdbcTemplate.update("UPDATE work_order SET created_at='2026-10-12 10:00:00' WHERE id=?", orderId);

        mockMvc.perform(get("/api/v1/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].submittedAt").value("2026-10-12T00:15:00+08:00"));
        mockMvc.perform(get("/api/v1/admin/orders/" + orderId)
                        .header("Authorization", "Bearer " + token(adminId, RoleCode.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.progress[0].submittedAt").value("2026-10-12T00:15:00+08:00"))
                .andExpect(jsonPath("$.data.order.createdAt").value("2026-10-12T10:00:00+08:00"));
        assertThat(jdbcTemplate.queryForObject("SELECT submitted_at FROM work_order_progress WHERE order_id=?",
                java.time.LocalDateTime.class, orderId)).isEqualTo(java.time.LocalDateTime.of(2026, 10, 11, 16, 15));
    }

    @Test
    void legacyInstallerCompletionEndpointIsRetiredWithoutChangingOrder() throws Exception {
        for (String prefix : new String[] {"/api", "/api/v1"}) {
            mockMvc.perform(post(prefix + "/orders/" + orderId + "/completion")
                            .header("Authorization", "Bearer " + installerToken)
                            .contentType("application/json").content("{}"))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.error").value("COMPLETION_ENDPOINT_RETIRED"));
        }
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId))
                .isEqualTo("PENDING_VISIT");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Long.class)).isZero();
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

    @Test
    void adminCanMaintainProgressImagesAfterCompletionWhileInstallerRemainsSealed() throws Exception {
        String progressResponse = mockMvc.perform(post("/api/orders/" + orderId + "/progress")
                        .header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content("{\"description\":\"施工记录\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long progressId = objectMapper.readTree(progressResponse).at("/data/id").asLong();
        jdbcTemplate.update("UPDATE work_order SET order_status='REVIEWED' WHERE id=?", orderId);

        MockMultipartFile adminImage = new MockMultipartFile("file", "admin-fix.png", "image/png", PNG);
        String uploadResponse = mockMvc.perform(multipart("/api/files/upload").file(adminImage)
                        .param("businessType", "PROGRESS")
                        .param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long fileId = objectMapper.readTree(uploadResponse).at("/data/id").asLong();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE "
                + "business_type='PROGRESS' AND business_id=? AND file_id=?", Long.class, progressId, fileId))
                .isEqualTo(1L);

        MockMultipartFile installerImage = new MockMultipartFile("file", "sealed.png", "image/png", PNG);
        mockMvc.perform(multipart("/api/files/upload").file(installerImage)
                        .param("businessType", "PROGRESS")
                        .param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS")
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("PROGRESS_SEALED"));

        mockMvc.perform(delete("/api/files/" + fileId + "/relations")
                        .param("businessType", "PROGRESS")
                        .param("businessId", String.valueOf(progressId))
                        .param("usageType", "PROGRESS")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE "
                + "business_type='PROGRESS' AND business_id=? AND file_id=?", Long.class, progressId, fileId))
                .isZero();
    }

    @Test
    void confirmationIsIndependentAndInstallerCanKeepSubmittingWhilePriorRecordsAreUnconfirmed() throws Exception {
        JsonNode first = submit("first-progress", "第一次施工", "");
        long progressId = first.path("id").asLong();
        String version = first.path("contentVersion").asText();
        assertThat(first.path("confirmationStatus").asText()).isEqualTo("PENDING");
        JsonNode second = submit("second-progress", "第二次施工", "");
        assertThat(second.path("id").asLong()).isNotEqualTo(progressId);
        Integer orderVersion = jdbcTemplate.queryForObject("SELECT version FROM work_order WHERE id=?", Integer.class, orderId);
        JsonNode confirmed = confirm(progressId, version, customerToken);
        assertThat(confirmed.path("customerConfirmed").asBoolean()).isTrue();
        String time = confirmed.path("customerConfirmedAt").asText();
        assertThat(OffsetDateTime.parse(time).toInstant()).isCloseTo(Instant.now(), org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(confirm(progressId, version, customerToken).path("customerConfirmedAt").asText()).isEqualTo(time);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress_confirmation", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT version FROM work_order WHERE id=?", Integer.class, orderId)).isEqualTo(orderVersion);
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId)).isEqualTo("IN_PROGRESS");
        submit("third-progress", "第三次施工", "");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='PROGRESS_SUBMITTED'", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForList("SELECT recipient_phone FROM sms_notification", String.class)).containsOnly("13830000001");
    }

    @Test
    void finalCompletionDoesNotRequireProgressConfirmationAndPendingProgressCanBeAcknowledgedAfterCompletion() throws Exception {
        JsonNode first = submit("finish-before-progress-confirm", "施工记录仍待客户确认", "");
        mockMvc.perform(post("/api/orders/" + orderId + "/confirm-completion").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.statusCode").value("PENDING_REVIEW"));
        confirm(first.path("id").asLong(), first.path("contentVersion").asText(), customerToken);
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId)).isEqualTo("PENDING_REVIEW");
        assertThat(submit("finish-before-progress-confirm", "施工记录仍待客户确认", "").path("id").asLong()).isEqualTo(first.path("id").asLong());
        mockMvc.perform(post("/api/orders/" + orderId + "/progress").header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content("{\"description\":\"新的提交\",\"requestId\":\"new-after-finish\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isEqualTo(1);
    }

    @Test
    void confirmationChecksCustomerOwnershipAndAdminCorrectionNeedsNoSecondConfirmation() throws Exception {
        JsonNode first = submit("ownership-progress", "原施工说明", "");
        long id = first.path("id").asLong(); String version = first.path("contentVersion").asText();
        String body = "{\"contentVersion\":\"" + version + "\"}";
        String path = "/api/orders/" + orderId + "/progress/" + id + "/confirm";
        for (String caller : java.util.List.of(adminToken, installerToken))
            mockMvc.perform(post(path).header("Authorization", "Bearer " + caller).contentType("application/json").content(body))
                    .andExpect(status().isForbidden());
        mockMvc.perform(post(path).header("Authorization", "Bearer " + token(otherCustomerId, RoleCode.CUSTOMER))
                        .contentType("application/json").content(body)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/orders/" + orderId + "/progress/999999/confirm").header("Authorization", "Bearer " + customerToken)
                        .contentType("application/json").content(body)).andExpect(status().isNotFound());
        confirm(id, version, customerToken);
        progressService.editForAdmin(new AuthenticatedUser(adminId, null, "管理员", null, Set.of(RoleCode.ADMIN)), orderId, id, "管理员修订说明", null);
        mockMvc.perform(post(path).header("Authorization", "Bearer " + customerToken).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_CONTENT_CHANGED"));
        JsonNode changed = progressList().get(0);
        assertThat(changed.path("customerConfirmed").asBoolean()).isFalse();
        assertThat(changed.path("confirmationStatus").asText()).isEqualTo("ADMIN_EDITED");
        assertThat(changed.path("adminEditedBy").asLong()).isEqualTo(adminId);
        mockMvc.perform(post(path).header("Authorization", "Bearer " + customerToken).contentType("application/json")
                        .content("{\"contentVersion\":\"" + changed.path("contentVersion").asText() + "\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_ADMIN_APPROVED"));
        assertThat(jdbcTemplate.queryForList("SELECT content_snapshot FROM work_order_progress_confirmation ORDER BY id", String.class))
                .hasSize(1).anyMatch(value -> value.contains("原施工说明"));
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void adminReplacesOneOfManyProgressRecordsWithPhotoAndVideoAfterCompletion() throws Exception {
        JsonNode first = submit("admin-record-1", "第一次施工", "");
        long oldImage = uploadImage("old.png");
        JsonNode second = submit("admin-record-2", "第二次施工", ",\"fileIds\":[" + oldImage + "]");
        JsonNode third = submit("admin-record-3", "第三次施工", "");
        confirm(second.path("id").asLong(), second.path("contentVersion").asText(), customerToken);
        jdbcTemplate.update("UPDATE work_order SET order_status='PENDING_REVIEW' WHERE id=?", orderId);

        long adminImage = uploadAsAdmin("corrected.png", "image/png", PNG);
        byte[] mp4 = new byte[] {0, 0, 0, 0x18, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d,
                0, 0, 0, 0x69, 0x73, 0x6f, 0x6d, 0x6d, 0x70, 0x34, 0x32};
        long adminVideo = uploadAsAdmin("corrected.mp4", "video/mp4", mp4);
        String url = "/api/v1/orders/" + orderId + "/progress/" + second.path("id").asLong();
        String body = objectMapper.writeValueAsString(java.util.Map.of("contentVersion", second.path("contentVersion").asText(),
                "description", "管理员覆盖第二次", "fileIds", List.of(adminImage, adminVideo)));

        for (String caller : List.of(installerToken, customerToken))
            mockMvc.perform(put(url).header("Authorization", "Bearer " + caller)
                            .contentType("application/json").content(body)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/orders/" + orderId + "/progress/" + first.path("id").asLong())
                        .header("Authorization", "Bearer " + adminToken).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_CONTENT_CHANGED"));
        mockMvc.perform(put(url).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(second.path("id").asLong()))
                .andExpect(jsonPath("$.data.description").value("管理员覆盖第二次"))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andExpect(jsonPath("$.data.images[1].id").value(adminVideo))
                .andExpect(jsonPath("$.data.confirmationStatus").value("ADMIN_EDITED"))
                .andExpect(jsonPath("$.data.adminEditedBy").value(adminId));

        JsonNode records = progressList();
        assertThat(records).hasSize(3);
        assertThat(records.get(0).path("description").asText()).isEqualTo("第一次施工");
        assertThat(records.get(1).path("description").asText()).isEqualTo("管理员覆盖第二次");
        assertThat(records.get(2).path("description").asText()).isEqualTo("第三次施工");
        assertThat(records.get(1).path("submittedAt").asText()).isEqualTo(second.path("submittedAt").asText());
        assertThat(records.get(1).path("adminEditedAt").asText()).isNotBlank();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE business_type='PROGRESS' AND business_id=?",
                Long.class, second.path("id").asLong())).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE file_id=?", Long.class, oldImage)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress_confirmation", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId)).isEqualTo("PENDING_REVIEW");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='PROGRESS_SUBMITTED'", Long.class)).isEqualTo(3L);

        mockMvc.perform(put(url).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_CONTENT_CHANGED"));
    }

    @Test
    void installerLaterAttachmentChangeRestoresCustomerConfirmationRequirement() throws Exception {
        JsonNode record = submit("admin-then-installer", "师傅第一次提交", "");
        long progressId = record.path("id").asLong();
        String url = "/api/orders/" + orderId + "/progress/" + progressId;
        String correction = objectMapper.writeValueAsString(java.util.Map.of("contentVersion", record.path("contentVersion").asText(),
                "description", "管理员修订", "fileIds", List.of()));
        mockMvc.perform(put(url).header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(correction))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.confirmationStatus").value("ADMIN_EDITED"));
        mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "installer-after-admin.png", "image/png", PNG))
                        .param("businessType", "PROGRESS").param("businessId", Long.toString(progressId))
                        .param("usageType", "PROGRESS").header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isOk());
        JsonNode changed = progressList().get(0);
        assertThat(changed.path("confirmationStatus").asText()).isEqualTo("PENDING");
        confirm(progressId, changed.path("contentVersion").asText(), customerToken);
        assertThat(progressList().get(0).path("customerConfirmed").asBoolean()).isTrue();
    }

    private long uploadAsAdmin(String name, String mime, byte[] content) throws Exception {
        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", name, mime, content))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    @Test
    void imageChangeInvalidatesOnlyProgressAcknowledgementAndFailedSubmissionsDoNotNotify() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/progress").header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content("{\"description\":\"失败记录\",\"fileIds\":[999999],\"requestId\":\"failed-image\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Integer.class)).isZero();
        long file = uploadImage("confirm-image.png"), second = uploadImage("confirm-image-2.png");
        JsonNode record = submit("two-images-once", "两张施工照片", ",\"fileIds\":[" + file + "," + second + "]");
        long id = record.path("id").asLong(); confirm(id, record.path("contentVersion").asText(), customerToken);
        mockMvc.perform(delete("/api/files/" + file + "/relations").header("Authorization", "Bearer " + adminToken)
                        .param("businessType", "PROGRESS").param("businessId", String.valueOf(id)).param("usageType", "PROGRESS"))
                .andExpect(status().isOk());
        assertThat(progressList().get(0).path("confirmationStatus").asText()).isEqualTo("ADMIN_EDITED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentRetriesCreateOneProgressOneSmsAndOneConfirmation() throws Exception {
        var installer = new AuthenticatedUser(installerId, null, "师傅", null, Set.of(RoleCode.INSTALLER));
        var customer = new AuthenticatedUser(customerId, null, "客户", null, Set.of(RoleCode.CUSTOMER));
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> progressService.submitProgress(installer, orderId, new WorkProgressService.ProgressCommand("并发进度", java.util.List.of(), "concurrent-progress")));
            var second = pool.submit(() -> progressService.submitProgress(installer, orderId, new WorkProgressService.ProgressCommand("并发进度", java.util.List.of(), "concurrent-progress")));
            var record = first.get(10, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(second.get(10, java.util.concurrent.TimeUnit.SECONDS).id()).isEqualTo(record.id());
            var confirmed = pool.submit(() -> confirmationService.confirm(customer, orderId, record.id(), record.contentVersion()));
            var retry = pool.submit(() -> confirmationService.confirm(customer, orderId, record.id(), record.contentVersion()));
            assertThat(retry.get(10, java.util.concurrent.TimeUnit.SECONDS).customerConfirmedAt())
                    .isEqualTo(confirmed.get(10, java.util.concurrent.TimeUnit.SECONDS).customerConfirmedAt());
        }
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_progress_confirmation", Integer.class)).isEqualTo(1);
    }

    private JsonNode submit(String token, String description, String extra) throws Exception {
        String response = mockMvc.perform(post("/api/orders/" + orderId + "/progress").header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content("{\"description\":\"" + description + "\",\"requestId\":\"" + token + "\"" + extra + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
    private JsonNode confirm(long id, String version, String caller) throws Exception {
        String response = mockMvc.perform(post("/api/orders/" + orderId + "/progress/" + id + "/confirm").header("Authorization", "Bearer " + caller)
                        .contentType("application/json").content("{\"contentVersion\":\"" + version + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }
    private JsonNode progressList() throws Exception {
        String response = mockMvc.perform(get("/api/orders/" + orderId + "/progress").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private long uploadImage() throws Exception {
        return uploadImage("completion.png");
    }

    private long uploadImage(String fileName) throws Exception {
        MockMultipartFile image = new MockMultipartFile("file", fileName, "image/png", PNG);
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
