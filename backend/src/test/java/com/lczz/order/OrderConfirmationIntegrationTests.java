package com.lczz.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.order.service.OrderConfirmationService;
import com.lczz.progress.service.WorkProgressService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:order_confirmation;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "lczz.file.local-root=target/test-order-confirmation-storage"
})
class OrderConfirmationIntegrationTests {
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
            0, 0, 0, 0x0d, 0x49, 0x48, 0x44, 0x52};
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper json;
    @Autowired OrderConfirmationService confirmationService;
    @Autowired WorkProgressService progressService;
    @Autowired PlatformTransactionManager transactionManager;
    private long adminId;
    private long installerId;
    private long customerId;
    private long otherCustomerId;
    private long dealerId;
    private long orderId;
    private String admin;
    private String installer;
    private String customer;

    @BeforeEach
    void setUp() {
        for (String table : List.of("business_file_relation", "file_asset", "work_order_progress", "work_order_review",
                "material_request_item", "material_request", "work_order_status_history", "work_order_assignment",
                "work_order", "user_wechat_identity", "sys_user_role", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        adminId = user("13999000001", RoleCode.ADMIN);
        installerId = user("13999000002", RoleCode.INSTALLER);
        customerId = user("13999000003", RoleCode.CUSTOMER);
        otherCustomerId = user("13999000004", RoleCode.CUSTOMER);
        dealerId = user("13999000005", RoleCode.DEALER);
        admin = token(adminId, RoleCode.ADMIN);
        installer = token(installerId, RoleCode.INSTALLER);
        customer = token(customerId, RoleCode.CUSTOMER);
        orderId = order("WO-CONFIRM-99", customerId, "IN_PROGRESS");
    }

    @Test
    void boundCustomerConfirmsOnceThenReviewsAndConfirmationTimeRemainsStable() throws Exception {
        JsonNode confirmed = data(post(confirmPath(orderId)), customer);
        assertThat(confirmed.path("statusCode").asText()).isEqualTo("PENDING_REVIEW");
        String confirmedAt = confirmed.path("customerConfirmedAt").asText();
        assertThat(java.time.OffsetDateTime.parse(confirmedAt).toInstant())
                .isCloseTo(java.time.Instant.now(), org.assertj.core.api.Assertions.within(5, java.time.temporal.ChronoUnit.SECONDS));
        assertThat(confirmed.path("customerConfirmedBy").asLong()).isEqualTo(customerId);
        mvc.perform(post(confirmPath(orderId)).header("Authorization", "Bearer " + customer))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDER_NOT_CONFIRMABLE"));
        data(post("/api/orders/evaluation").contentType("application/json")
                .content("{\"orderId\":" + orderId + ",\"score\":5,\"content\":\"满意\"}"), customer);
        JsonNode detail = data(get("/api/v1/admin/orders/" + orderId), admin).path("order");
        assertThat(detail.path("statusCode").asText()).isEqualTo("REVIEWED");
        assertThat(detail.path("customerConfirmedAt").asText()).isEqualTo(confirmedAt);
        JsonNode list = data(get("/api/orders/list"), admin).path("list").get(0);
        assertThat(list.path("customerConfirmedAt").asText()).isEqualTo(confirmedAt);
        assertThat(confirmationCount()).isEqualTo(1);
    }

    @Test
    void bothPrefixesEnforceRoleAndBoundCustomerIncludingDealerCustomers() throws Exception {
        for (String prefix : List.of("/api", "/api/v1")) {
            String endpoint = prefix + "/orders/" + orderId + "/confirm-completion";
            mvc.perform(post(endpoint)).andExpect(status().isUnauthorized());
            for (String caller : List.of(admin, installer)) {
                mvc.perform(post(endpoint).header("Authorization", "Bearer " + caller))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
            }
            for (String caller : List.of(token(otherCustomerId, RoleCode.CUSTOMER), token(dealerId, RoleCode.DEALER))) {
                mvc.perform(post(endpoint).header("Authorization", "Bearer " + caller))
                        .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist());
            }
            long dealerOrder = order("DEALER-" + prefix.replace('/', '-'), dealerId, "IN_PROGRESS");
            data(post(prefix + "/orders/" + dealerOrder + "/confirm-completion"), token(dealerId, RoleCode.DEALER));
        }
        assertThat(orderStatus(orderId)).isEqualTo("IN_PROGRESS");
    }

    @Test
    void rejectsInactiveDeletedUnboundAndMalformedOrders() throws Exception {
        for (String state : List.of("PENDING_VISIT", "PENDING_REVIEW", "REVIEWED", "CANCELLED")) {
            long id = order("WO-" + state, customerId, state);
            mvc.perform(post(confirmPath(id)).header("Authorization", "Bearer " + customer))
                    .andExpect(status().isConflict());
            assertThat(orderStatus(id)).isEqualTo(state);
        }
        jdbc.update("UPDATE work_order SET deleted=TRUE WHERE id=?", orderId);
        for (long id : List.of(orderId, Long.MAX_VALUE)) {
            mvc.perform(post(confirmPath(id)).header("Authorization", "Bearer " + customer))
                    .andExpect(status().isNotFound());
        }
        for (String invalid : List.of("0", "-1", "abc", "9223372036854775808")) {
            mvc.perform(post("/api/orders/" + invalid + "/confirm-completion").header("Authorization", "Bearer " + customer))
                    .andExpect(status().isBadRequest());
        }
        assertThat(confirmationCount()).isZero();
    }

    @Test
    void confirmationSealsProgressAndAllAttachmentMutationEndpoints() throws Exception {
        long image = upload();
        long extraImage = upload();
        JsonNode progress = data(post("/api/orders/" + orderId + "/progress").contentType("application/json")
                .content("{\"description\":\"封存前的施工记录\",\"fileIds\":[" + image + "]}"), installer);
        long progressId = progress.path("id").asLong();
        data(post(confirmPath(orderId)), customer);
        mvc.perform(post("/api/orders/" + orderId + "/progress").header("Authorization", "Bearer " + installer)
                        .contentType("application/json").content("{\"description\":\"封存后追加\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("ORDER_NOT_ACCEPTING_PROGRESS"));
        String relation = "{\"businessType\":\"PROGRESS\",\"businessId\":" + progressId + ",\"usageType\":\"PROGRESS\"}";
        for (String caller : List.of(installer, admin)) {
            mvc.perform(post("/api/files/" + extraImage + "/relations").header("Authorization", "Bearer " + caller)
                            .contentType("application/json").content(relation))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_SEALED"));
            mvc.perform(delete("/api/files/" + image + "/relations").header("Authorization", "Bearer " + caller)
                            .param("businessType", "PROGRESS").param("businessId", String.valueOf(progressId)).param("usageType", "PROGRESS"))
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_SEALED"));
        }
        mvc.perform(multipart("/api/files/upload").file(new MockMultipartFile("file", "late.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + installer).param("businessType", "PROGRESS")
                        .param("businessId", String.valueOf(progressId)).param("usageType", "PROGRESS"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("PROGRESS_SEALED"));
        mvc.perform(put("/api/orders/" + orderId + "/progress/" + progressId).header("Authorization", "Bearer " + installer)
                        .contentType("application/json").content("{\"description\":\"修改封存说明\"}"))
                .andExpect(status().isNotFound());
        JsonNode sealed = data(get("/api/orders/" + orderId + "/progress"), customer);
        assertThat(sealed).hasSize(1);
        assertThat(sealed.get(0).path("description").asText()).isEqualTo("封存前的施工记录");
        assertThat(sealed.get(0).path("images")).hasSize(1);
        mvc.perform(get(sealed.get(0).at("/images/0/url").asText())).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM file_asset", Integer.class)).isEqualTo(2);
    }

    @Test
    void administratorCannotBypassConfirmationOrReviewThroughGenericStatusUpdates() throws Exception {
        for (String target : List.of("PENDING_REVIEW", "REVIEWED", "completed", "已完成", "done")) {
            mvc.perform(patch("/api/orders/" + orderId + "/status").header("Authorization", "Bearer " + admin)
                            .contentType("application/json").content("{\"status\":\"" + target + "\"}"))
                    .andExpect(status().isConflict());
        }
        data(post(confirmPath(orderId)), customer);
        mvc.perform(patch("/api/orders/" + orderId + "/status").header("Authorization", "Bearer " + admin)
                        .contentType("application/json").content("{\"status\":\"REVIEWED\"}"))
                .andExpect(status().isConflict());
        data(post("/api/orders/" + orderId + "/cancel").contentType("application/json").content("{\"reason\":\"保留作废规则\"}"), admin);
        assertThat(orderStatus(orderId)).isEqualTo("CANCELLED");
    }

    @Test
    void completedFilterCombinesExactStatesAndLegacyCompletionKeepsNoCustomerConfirmationTime() throws Exception {
        data(post(confirmPath(orderId)), customer);
        long legacy = order("WO-LEGACY", customerId, "REVIEWED");
        jdbc.update("INSERT INTO work_order_progress(order_id, installer_user_id, progress_type, description) "
                + "VALUES (?, ?, 'COMPLETION', '历史完工记录')", legacy, installerId);
        order("WO-ACTIVE", customerId, "IN_PROGRESS");
        order("WO-CANCELLED", customerId, "CANCELLED");
        order("WO-OTHER-COMPLETED", otherCustomerId, "PENDING_REVIEW");
        for (String completed : List.of("COMPLETED", "已完成", "done")) {
            assertThat(data(get("/api/orders/list").param("status", completed), admin).path("total").asInt()).isEqualTo(3);
            assertThat(data(get("/api/orders/list").param("status", completed), customer).path("total").asInt()).isEqualTo(2);
        }
        assertThat(data(get("/api/orders/list").param("status", "PENDING_REVIEW"), admin).path("total").asInt()).isEqualTo(2);
        assertThat(data(get("/api/orders/list").param("status", "REVIEWED"), admin).path("total").asInt()).isEqualTo(1);
        JsonNode detail = data(get("/api/admin/orders/" + legacy), admin);
        assertThat(detail.at("/progress/0/type").asText()).isEqualTo("COMPLETION");
        assertThat(detail.path("order").has("customerConfirmedAt")).isFalse();
    }

    @Test
    void concurrentConfirmationsProduceOnlyOneTransition() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            var action = (java.util.concurrent.Callable<Integer>) () -> {
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                try { confirmationService.confirm(actor(customerId, RoleCode.CUSTOMER), orderId); return 200; }
                catch (BusinessException exception) { return exception.getStatus(); }
            };
            Future<Integer> first = pool.submit(action);
            Future<Integer> second = pool.submit(action);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(confirmationCount()).isEqualTo(1);
    }

    @Test
    void progressRacingAnUncommittedConfirmationCannotWriteAfterItCommits() throws Exception {
        try (var pool = Executors.newSingleThreadExecutor()) {
            AtomicReference<Future<Integer>> pending = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            new TransactionTemplate(transactionManager).executeWithoutResult(transaction -> {
                confirmationService.confirm(actor(customerId, RoleCode.CUSTOMER), orderId);
                pending.set(pool.submit(() -> {
                    started.countDown();
                    try {
                        progressService.submitProgress(actor(installerId, RoleCode.INSTALLER), orderId,
                                new WorkProgressService.ProgressCommand("并发施工提交", List.of()));
                        return 200;
                    } catch (BusinessException exception) { return exception.getStatus(); }
                }));
                try { assertThat(started.await(5, TimeUnit.SECONDS)).isTrue(); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
            });
            assertThat(pending.get().get(10, TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order_progress WHERE order_id=?", Integer.class, orderId)).isZero();
    }

    private JsonNode data(MockHttpServletRequestBuilder request, String token) throws Exception {
        return json.readTree(mvc.perform(request.header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }

    private long upload() throws Exception {
        String response = mvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", "site.png", "image/png", PNG))
                        .header("Authorization", "Bearer " + installer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).at("/data/id").asLong();
    }

    private int confirmationCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? AND change_reason=?",
                Integer.class, orderId, OrderConfirmationService.CONFIRMATION_REASON);
    }

    private String orderStatus(long id) {
        return jdbc.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, id);
    }

    private String confirmPath(long id) { return "/api/orders/" + id + "/confirm-completion"; }

    private long order(String number, long customerUserId, String state) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus(state);
        order.setCustomerUserId(customerUserId);
        order.setCustomerName("测试客户");
        order.setCustomerPhone("13999000003");
        order.setInstallerUserId(installerId);
        order.setDetailedAddress("测试地址");
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy(adminId);
        orderMapper.insert(order);
        return order.getId();
    }

    private long user(String phone, RoleCode role) {
        UserEntity user = new UserEntity();
        user.setRealName(role.name());
        user.setPhone(phone);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?", user.getId(), role.name());
        return user.getId();
    }

    private AuthenticatedUser actor(long id, RoleCode role) {
        return new AuthenticatedUser(id, null, "测试用户", null, Set.of(role));
    }

    private String token(long id, RoleCode role) { return jwtService.issue(actor(id, role)).value(); }
}
