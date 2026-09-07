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
import com.lczz.notification.config.SmsProperties;
import com.lczz.order.service.DealerAppointmentService;
import com.lczz.order.service.OrderService;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DealerAppointmentIntegrationTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper users;
    @Autowired JwtService tokens;
    @Autowired SmsProperties sms;
    @Autowired DealerAppointmentService appointments;
    @Autowired OrderService orders;
    @MockitoBean WechatIdentityGateway wechat;
    long admin, dealer, otherDealer, customer, installer;
    Map<Long, RoleCode> roles = new HashMap<>();

    @BeforeEach
    void setup() {
        for (String table : List.of("sms_notification", "business_file_relation", "file_asset", "work_order_review",
                "work_order_progress", "material_request_item", "material_request", "work_order_assignment",
                "work_order_status_history", "work_order", "user_wechat_identity", "sys_user_role", "sys_user")) {
            jdbc.update("DELETE FROM " + table);
        }
        admin = user("管理员", "13900000001", RoleCode.ADMIN);
        dealer = user("预约经销商", "13900000002", RoleCode.DEALER);
        otherDealer = user("其他经销商", "13900000003", RoleCode.DEALER);
        customer = user("现有客户", "13800138000", RoleCode.CUSTOMER);
        installer = user("安装师傅", "13900000004", RoleCode.INSTALLER);
        sms.setEnabled(false);
        sms.setAdminPhones("13900000001");
    }

    @AfterEach
    void resetSms() { sms.setAdminPhones(null); }

    @Test
    void dealerCreatesUnassignedOrderWithSourceCustomerAndSingleNotification() throws Exception {
        var body = body("13800138000");
        long file = upload(dealer);
        body.put("fileIds", List.of(file));
        JsonNode receipt = create(dealer, body);
        long id = receipt.path("id").asLong();
        assertThat(receipt.path("statusCode").asText()).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(receipt.has("customerPhone")).isFalse();
        JsonNode detail = data(get("/api/v1/admin/orders/" + id), admin).path("order");
        assertThat(detail.path("orderSource").asText()).isEqualTo("DEALER_APPOINTMENT");
        assertThat(detail.path("dealerUserId").asLong()).isEqualTo(dealer);
        assertThat(detail.path("dealerName").asText()).isEqualTo("预约经销商");
        assertThat(detail.path("dealer").path("id").asLong()).isEqualTo(dealer);
        assertThat(detail.path("dealer").path("name").asText()).isEqualTo("预约经销商");
        assertThat(detail.path("customerUserId").asLong()).isEqualTo(customer);
        assertThat(detail.path("selectedMasterList").size()).isZero();
        assertThat(detail.path("fileList").size()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order_assignment", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='DEALER_APPOINTMENT_CREATED' AND notification_status='SKIPPED'", Integer.class)).isEqualTo(1);
        assertThat(create(dealer, body).path("id").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isEqualTo(1);
        assertThat(data(get("/api/orders/list?status=PENDING_ASSIGNMENT"), admin).path("total").asInt()).isEqualTo(1);
        assertThat(data(get("/api/orders/list"), installer).path("total").asInt()).isZero();
        JsonNode customerView = data(get("/api/orders/detail/" + id), customer);
        assertThat(customerView.hasNonNull("dealer")).isFalse();
        mvc.perform(auth(get("/api/orders/detail/" + id), dealer)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/files/" + file + "/url"), customer)).andExpect(status().isOk());
        mvc.perform(auth(get("/api/files/" + file + "/url"), otherDealer)).andExpect(status().isForbidden());
    }

    @Test
    void adminCanSaveEditAndAssignDealerOrderWithoutChangingLegacyCreation() throws Exception {
        long id = create(dealer, body("13800138000")).path("id").asLong();
        Map<String, Object> edit = adminBody();
        edit.put("description", "管理员补充品牌和主机信息");
        edit.put("customerPhone", "13800138001");
        edit.put("masterIds", List.of());
        edit.put("orderStartTime", "");
        edit.put("orderEndTime", "");
        JsonNode saved = data(put("/api/orders/" + id).contentType("application/json").content(json.writeValueAsString(edit)), admin);
        assertThat(saved.path("statusCode").asText()).isEqualTo("PENDING_ASSIGNMENT");
        assertThat(saved.path("customerPhone").asText()).isEqualTo("13800138001");
        assertThat(saved.hasNonNull("customerUserId")).isFalse();
        String audit = jdbc.queryForObject("SELECT before_json FROM operation_audit_log WHERE operation_type='ORDER_CUSTOMER_REBIND' AND business_id=?", String.class, Long.toString(id));
        assertThat(audit).contains("138****8000").doesNotContain("13800138000");
        assertThat(saved.path("description").asText()).isEqualTo(edit.get("description"));
        mvc.perform(auth(post("/api/orders/" + id + "/assign-master").contentType("application/json")
                .content(json.writeValueAsString(Map.of("masterIds", List.of(installer)))), admin)).andExpect(status().isBadRequest());
        edit.put("orderStartTime", "2026-09-10T09:00:00+08:00");
        edit.put("orderEndTime", "2026-09-10T12:00:00+08:00");
        edit.put("masterIds", List.of(installer));
        JsonNode assigned = data(put("/api/orders/" + id).contentType("application/json").content(json.writeValueAsString(edit)), admin);
        assertThat(assigned.path("statusCode").asText()).isEqualTo("PENDING_VISIT");
        assertThat(assigned.path("dealer").path("id").asLong()).isEqualTo(dealer);
        assertThat(assigned.path("selectedMasterList").get(0).path("id").asLong()).isEqualTo(installer);
        data(put("/api/orders/" + id).contentType("application/json").content(json.writeValueAsString(edit)), admin);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order_assignment WHERE order_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? AND to_status='PENDING_VISIT'", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_notification WHERE event_type='INSTALLER_ASSIGNED'", Integer.class)).isEqualTo(1);
        JsonNode legacy = data(post("/api/orders").contentType("application/json").content(json.writeValueAsString(adminBody())), admin);
        assertThat(legacy.path("orderSource").asText()).isEqualTo("ADMIN");
        assertThat(legacy.path("statusCode").asText()).isEqualTo("PENDING_VISIT");
        edit.put("masterIds", List.of());
        mvc.perform(auth(post("/api/orders").contentType("application/json").content(json.writeValueAsString(edit)), admin)).andExpect(status().isBadRequest());
    }

    @Test
    void existingAssignEndpointSupportsFirstAssignmentAfterSavingSchedule() throws Exception {
        long id = create(dealer, body("13800138000")).path("id").asLong();
        var edit = adminBody(); edit.put("masterIds", List.of());
        data(put("/api/orders/" + id).contentType("application/json").content(json.writeValueAsString(edit)), admin);
        JsonNode assigned = data(post("/api/orders/" + id + "/assign-master").contentType("application/json")
                .content(json.writeValueAsString(Map.of("masterIds", List.of(installer)))), admin);
        assertThat(assigned.path("statusCode").asText()).isEqualTo("PENDING_VISIT");
    }

    @Test
    void newPhoneIsPrecreatedAndWechatLoginReusesCustomerIdentity() throws Exception {
        var first = create(dealer, body("+8613700137000"));
        create(otherDealer, body("13700137000"));
        long customerId = jdbc.queryForObject("SELECT customer_user_id FROM work_order WHERE id=?", Long.class, first.path("id").asLong());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone='13700137000'", Integer.class)).isEqualTo(1);
        when(wechat.exchangeLoginCode("appointment-login")).thenReturn(new WechatIdentity("wx-app", "appointment-open-id", "union-id"));
        when(wechat.exchangePhoneCode("appointment-phone")).thenReturn("13700137000");
        mvc.perform(post("/api/auth/wechat/login").contentType("application/json").content("{\"code\":\"appointment-login\"}")).andExpect(status().isOk());
        String login = mvc.perform(post("/api/auth/wechat/bind-phone").contentType("application/json")
                        .content("{\"code\":\"appointment-login\",\"phoneCode\":\"appointment-phone\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(login).at("/data/userInfo/id").asLong()).isEqualTo(customerId);
        assertThat(json.readTree(login).at("/data/userInfo/role").asText()).isEqualTo("customer");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone='13700137000'", Integer.class)).isEqualTo(1);
        create(dealer, body("13900000003"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role ur JOIN sys_role r ON r.id=ur.role_id WHERE ur.user_id=? AND r.role_code='DEALER'", Integer.class, otherDealer)).isEqualTo(1);
    }

    @Test
    void authorizationAndRequestAllowlistPreventAssignmentAndOtherOrderWrites() throws Exception {
        var body = body("13800138000");
        for (long actor : List.of(admin, customer, installer)) {
            mvc.perform(auth(post("/api/v1/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(body)), actor))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(post("/api/v1/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(body))).andExpect(status().isUnauthorized());
        for (String field : List.of("masterIds", "installerUserId", "dealerUserId", "orderSource", "status", "adminRemark", "id")) {
            var tampered = new LinkedHashMap<>(body); tampered.put(field, "unexpected");
            mvc.perform(auth(post("/api/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(tampered)), dealer))
                    .andExpect(status().isBadRequest());
        }
        long id = create(otherDealer, body).path("id").asLong();
        for (var request : List.of(put("/api/orders/" + id).contentType("application/json").content(json.writeValueAsString(adminBody())),
                post("/api/orders/" + id + "/assign-master").contentType("application/json").content(json.writeValueAsString(Map.of("masterIds", List.of(installer)))),
                post("/api/orders/" + id + "/cancel"))) {
            mvc.perform(auth(request, dealer)).andExpect(status().isForbidden());
        }
        assertThatThrownBy(() -> orders.assign(actor(dealer), id, List.of(installer), "bypass"))
                .hasMessageContaining("仅管理员");
        assertThatThrownBy(() -> appointments.create(actor(customer), new DealerAppointmentService.Command(
                "request-1234567890", "空调安装", null, "客户", "13800138000", List.of("湖北省", "武汉市", "江岸区"), "1号", List.of())))
                .hasMessageContaining("仅经销商");
    }

    @Test
    void validatesPayloadAndRejectsChangedIdempotentRequest() throws Exception {
        var payload = body("13800138000");
        create(dealer, payload);
        payload.put("description", "changed");
        mvc.perform(auth(post("/api/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(payload)), dealer))
                .andExpect(status().isConflict());
        for (var invalid : List.of(Map.entry("customerPhone", "123"), Map.entry("taskType", "unknown"),
                Map.entry("customerName", " "), Map.entry("description", " "), Map.entry("addressArea", List.of("湖北省")),
                Map.entry("fileIds", Arrays.asList(1, 1)), Map.entry("fileIds", Arrays.asList(1, null)),
                Map.entry("requestId", "short"), Map.entry("description", "a".repeat(1001)))) {
            var request = body("13800138000"); request.put(invalid.getKey(), invalid.getValue());
            mvc.perform(auth(post("/api/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(request)), dealer)).andExpect(status().isBadRequest());
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order", Integer.class)).isEqualTo(1);
        assertThat(data(get("/api/orders/task-types"), dealer).size()).isEqualTo(4);
        mvc.perform(auth(get("/api/orders/task-types"), installer)).andExpect(status().isForbidden());
    }

    @Test
    void invalidOrStolenAttachmentRollsBackCustomerOrderAndSms() throws Exception {
        long stolen = upload(otherDealer);
        var payload = body("13700137001"); payload.put("fileIds", List.of(stolen));
        mvc.perform(auth(post("/api/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(payload)), dealer)).andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone='13700137001'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isZero();
        long owned = upload(dealer);
        payload.put("fileIds", List.of(owned));
        long id = create(dealer, payload).path("id").asLong();
        var reused = body("13800138000"); reused.put("fileIds", List.of(owned));
        mvc.perform(auth(post("/api/mini/dealer/appointments").contentType("application/json").content(json.writeValueAsString(reused)), dealer)).andExpect(status().isConflict());
        mvc.perform(auth(delete("/api/files/" + owned), dealer)).andExpect(status().isConflict());
        long extra = upload(dealer);
        mvc.perform(auth(post("/api/files/" + extra + "/relations").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("businessType", "ORDER", "businessId", id, "usageType", "ATTACHMENT"))), dealer))
                .andExpect(status().isForbidden());
    }

    @Test
    void pendingOrderCannotSkipAssignmentAndCanBeCancelled() throws Exception {
        long id = create(dealer, body("13800138000")).path("id").asLong();
        for (String state : List.of("PENDING_VISIT", "IN_PROGRESS", "PENDING_REVIEW")) {
            mvc.perform(auth(patch("/api/orders/" + id + "/status").contentType("application/json").content(json.writeValueAsString(Map.of("status", state))), admin)).andExpect(status().isConflict());
        }
        mvc.perform(auth(post("/api/orders/" + id + "/progress").contentType("application/json").content("{\"description\":\"施工\",\"fileIds\":[]}"), installer)).andExpect(status().isNotFound());
        mvc.perform(auth(post("/api/orders/" + id + "/confirm-completion"), customer)).andExpect(status().isConflict());
        assertThat(data(post("/api/orders/" + id + "/cancel"), admin).path("statusCode").asText()).isEqualTo("CANCELLED");
        mvc.perform(auth(post("/api/orders/" + id + "/assign-master").contentType("application/json").content(json.writeValueAsString(Map.of("masterIds", List.of(installer)))), admin)).andExpect(status().isConflict());
    }

    @Test
    void concurrentRetriesCreateOnlyOneOrderCustomerAndEvent() throws Exception {
        var payload = body("13700137002");
        String request = json.writeValueAsString(payload);
        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<Long> submit = () -> { start.await(); return data(post("/api/mini/dealer/appointments")
                    .contentType("application/json").content(request), dealer).path("id").asLong(); };
            var one = executor.submit(submit); var two = executor.submit(submit); start.countDown();
            assertThat(one.get(15, TimeUnit.SECONDS)).isEqualTo(two.get(15, TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM work_order", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE phone='13700137002'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sms_notification", Integer.class)).isEqualTo(1);
    }

    private Map<String, Object> body(String phone) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestId", UUID.randomUUID().toString());
        body.put("taskType", "AIR_CONDITIONING_INSTALL");
        body.put("description", "品牌、主机与内机安装信息");
        body.put("customerName", "预约客户"); body.put("customerPhone", phone);
        body.put("addressArea", List.of("湖北省", "武汉市", "江岸区")); body.put("addressDetail", "测试街道1号");
        body.put("fileIds", List.of()); return body;
    }
    private Map<String, Object> adminBody() {
        var body = body("13800138000"); body.remove("requestId"); body.remove("fileIds");
        body.put("orderStartTime", "2026-09-10T09:00:00+08:00"); body.put("orderEndTime", "2026-09-10T12:00:00+08:00");
        body.put("masterIds", List.of(installer)); return body;
    }
    private JsonNode create(long user, Map<String, Object> body) throws Exception {
        return data(post("/api/v1/dealer/appointments").contentType("application/json").content(json.writeValueAsString(body)), user);
    }
    private JsonNode data(MockHttpServletRequestBuilder request, long user) throws Exception {
        return json.readTree(mvc.perform(auth(request, user)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request, long user) {
        return request.header("Authorization", "Bearer " + tokens.issue(actor(user)).value());
    }
    private AuthenticatedUser actor(long user) { return new AuthenticatedUser(user, null, "测试用户", null, Set.of(roles.get(user))); }
    private long user(String name, String phone, RoleCode role) {
        UserEntity user = new UserEntity(); user.setRealName(name); user.setPhone(phone);
        user.setAccountStatus("ENABLED"); user.setAuditStatus("APPROVED"); user.setBlacklist(false); user.setDeleted(false);
        users.insert(user); roles.put(user.getId(), role);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?", user.getId(), role.name());
        return user.getId();
    }
    private long upload(long user) throws Exception {
        var png = new MockMultipartFile("file", "test.png", "image/png", Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+a1WQAAAAASUVORK5CYII="));
        String response = mvc.perform(multipart("/api/files/upload").file(png).header("Authorization", "Bearer " + tokens.issue(actor(user)).value()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).at("/data/id").asLong();
    }
}
