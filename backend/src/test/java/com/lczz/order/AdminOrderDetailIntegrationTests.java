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
import com.lczz.order.service.AdminOrderDetailService;
import java.time.LocalDateTime;
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
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_order_detail;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "lczz.file.local-root=target/test-admin-order-detail-storage"
})
@Transactional
class AdminOrderDetailIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminOrderDetailService detailService;

    private long adminId;
    private long installerId;
    private long customerId;
    private long dealerId;
    private long orderId;
    private String adminToken;
    private String installerToken;
    private String customerToken;

    @BeforeEach
    void setUp() {
        adminId = createUser("详情管理员", "13998000001", RoleCode.ADMIN);
        installerId = createUser("详情师傅", "13998000002", RoleCode.INSTALLER);
        customerId = createUser("详情客户", "13998000003", RoleCode.CUSTOMER);
        dealerId = createUser("详情经销商", "13998000004", RoleCode.DEALER);
        orderId = createOrder("WO-DETAIL-98");
        adminToken = token(adminId, RoleCode.ADMIN);
        installerToken = token(installerId, RoleCode.INSTALLER);
        customerToken = token(customerId, RoleCode.CUSTOMER);
    }

    @Test
    void emptyDetailReturnsStableEmptyValuesThroughBothApiPrefixes() throws Exception {
        for (String prefix : new String[] {"/api", "/api/v1"}) {
            mockMvc.perform(get(prefix + "/admin/orders/" + orderId).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.order.orderNo").value("WO-DETAIL-98"))
                    .andExpect(jsonPath("$.data.order.statusCode").value("PENDING_VISIT"))
                    .andExpect(jsonPath("$.data.order.fileList").isEmpty())
                    .andExpect(jsonPath("$.data.progress").isEmpty())
                    .andExpect(jsonPath("$.data.materialRequests").isEmpty())
                    .andExpect(jsonPath("$.data.review").doesNotExist());
        }
        assertThat(getData("/api/v1/admin/orders/" + orderId, adminToken).has("review")).isTrue();
        assertThat(jdbc.queryForObject("SELECT order_status FROM work_order WHERE id=?", String.class, orderId))
                .isEqualTo("PENDING_VISIT");
    }

    @Test
    void aggregatesSharedProgressAttachmentsMaterialSnapshotsAndReviewWithoutOtherOrders() throws Exception {
        long attachmentId = upload(adminToken, "attachment.png");
        postData("/api/files/" + attachmentId + "/relations", adminToken,
                "{\"businessType\":\"ORDER\",\"businessId\":" + orderId + ",\"usageType\":\"ATTACHMENT\"}");
        long progressImageId = upload(installerToken, "site.png");
        postData("/api/orders/" + orderId + "/progress", installerToken,
                "{\"description\":\"第一天铺设管线\",\"fileIds\":[" + progressImageId + "]}");
        postData("/api/orders/" + orderId + "/progress", installerToken,
                "{\"description\":\"第二天调试设备\"}");

        long unrelatedOrder = createOrder("WO-OTHER-98");
        postData("/api/orders/" + unrelatedOrder + "/progress", installerToken,
                "{\"description\":\"其他订单的进度\"}");
        long oldRequest = createRequest(orderId, "MR-OLD-98", "VOIDED", "首次申请", "调整用量");
        long newRequest = createRequest(orderId, "MR-NEW-98", "DONE", "追加耗材", null);
        createRequest(unrelatedOrder, "MR-OTHER-98", "PENDING", "其他订单", null);
        // Current product data differs from the submitted snapshots, which must remain readable even after deletion.
        jdbc.update("INSERT INTO product(product_code, product_name, category_id, model_spec, unit, deleted) "
                + "VALUES ('P-98', '改名后的产品', 1, '新规格', '箱', TRUE)");
        long productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code='P-98'", Long.class);
        for (long requestId : new long[] {oldRequest, newRequest}) {
            jdbc.update("INSERT INTO material_request_item(request_id, product_id, product_code_snapshot, "
                    + "product_name_snapshot, model_spec_snapshot, unit_snapshot, requested_quantity) "
                    + "VALUES (?, ?, 'P-98', '铜管快照', '6mm', '米', 2.5)", requestId, productId);
        }
        long completionImage = upload(installerToken, "completion.png");
        postData("/api/orders/" + orderId + "/completion", installerToken,
                "{\"description\":\"施工结束\",\"fileIds\":[" + completionImage + "]}");
        long reviewImage = upload(customerToken, "review.png");
        postData("/api/orders/evaluation", customerToken,
                "{\"orderId\":" + orderId + ",\"score\":5,\"liked\":true,\"content\":\"安装细致\","
                        + "\"labels\":[\"服务好\"],\"fileIds\":[" + reviewImage + "]}");

        JsonNode detail = getData("/api/v1/admin/orders/" + orderId, adminToken);
        assertThat(detail.at("/order/customerName").asText()).isEqualTo("详情客户");
        assertThat(detail.at("/order/address").asText()).isEqualTo("湖北省武汉市洪山区测试路98号");
        assertThat(detail.at("/order/selectedMasterList/0/masterName").asText()).isEqualTo("详情师傅");
        assertThat(detail.at("/order/statusCode").asText()).isEqualTo("REVIEWED");
        assertThat(detail.at("/order/orderStartTime").asText()).startsWith("2026-09-04T");
        assertThat(detail.at("/order/fileList/0/id").asLong()).isEqualTo(attachmentId);
        assertThat(detail.path("progress")).hasSize(3);
        assertThat(detail.at("/progress/0/description").asText()).isEqualTo("第一天铺设管线");
        assertThat(detail.at("/progress/1/description").asText()).isEqualTo("第二天调试设备");
        assertThat(detail.at("/progress/2/type").asText()).isEqualTo("COMPLETION");
        assertThat(detail.at("/progress/0/images/0/id").asLong()).isEqualTo(progressImageId);
        JsonNode miniProgress = getData("/api/orders/" + orderId + "/progress", customerToken);
        for (int index = 0; index < miniProgress.size(); index++) {
            for (String field : new String[] {"id", "description", "type", "submittedAt", "installerUserId"}) {
                assertThat(detail.path("progress").get(index).path(field)).isEqualTo(miniProgress.get(index).path(field));
            }
        }
        assertThat(detail.path("materialRequests")).hasSize(2);
        assertThat(detail.at("/materialRequests/0/id").asLong()).isEqualTo(newRequest);
        assertThat(detail.at("/materialRequests/0/remark").asText()).isEqualTo("追加耗材");
        assertThat(detail.at("/materialRequests/1/statusCode").asText()).isEqualTo("VOIDED");
        assertThat(detail.at("/materialRequests/1/voidReason").asText()).isEqualTo("调整用量");
        assertThat(detail.at("/materialRequests/0/materials/0/name").asText()).isEqualTo("铜管快照");
        assertThat(detail.at("/materialRequests/0/materials/0/spec").asText()).isEqualTo("6mm");
        assertThat(detail.at("/materialRequests/0/materials/0/unit").asText()).isEqualTo("米");
        assertThat(detail.at("/materialRequests/0/materials/0/count").asDouble()).isEqualTo(2.5);
        assertThat(detail.at("/materialRequests/0"))
                .isEqualTo(getData("/api/orders/" + orderId + "/materials", installerToken));
        assertThat(detail.at("/materialRequests/1"))
                .isEqualTo(getData("/api/preparation/detail/" + oldRequest, adminToken));
        assertThat(detail.at("/review/score").asInt()).isEqualTo(5);
        assertThat(detail.at("/review/content").asText()).isEqualTo("安装细致");
        assertThat(detail.at("/review/labels/0").asText()).isEqualTo("服务好");
        for (String path : new String[] {"/order/fileList/0/url", "/progress/0/images/0/url", "/review/images/0"}) {
            mockMvc.perform(get(detail.at(path).asText())).andExpect(status().isOk());
        }
    }

    @Test
    void allNonAdminRolesAreRejectedEvenForTheirOwnOrdersAndUnknownIds() throws Exception {
        for (String caller : new String[] {installerToken, customerToken, token(dealerId, RoleCode.DEALER)}) {
            for (String prefix : new String[] {"/api", "/api/v1"}) {
                for (long id : new long[] {orderId, Long.MAX_VALUE}) {
                    mockMvc.perform(get(prefix + "/admin/orders/" + id).header("Authorization", "Bearer " + caller))
                            .andExpect(status().isForbidden()).andExpect(jsonPath("$.data").doesNotExist());
                }
            }
        }
        // Existing scoped mini-program details are unaffected.
        getData("/api/orders/detail/" + orderId, customerToken);
        getData("/api/orders/detail/" + orderId, installerToken);
        assertThatThrownBy(() -> detailService.detail(actor(customerId, RoleCode.CUSTOMER), orderId))
                .isInstanceOf(BusinessException.class).hasMessage("仅管理员可查看后台订单详情");
    }

    @Test
    void anonymousRequestsAreRejected() throws Exception {
        for (String prefix : new String[] {"/api", "/api/v1"}) {
            mockMvc.perform(get(prefix + "/admin/orders/" + orderId))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void missingDeletedAndInvalidOrdersDoNotExposeRelatedData() throws Exception {
        createRequest(orderId, "MR-DELETED-98", "PENDING", "不应泄露", null);
        jdbc.update("UPDATE work_order SET deleted=TRUE WHERE id=?", orderId);
        for (long id : new long[] {orderId, Long.MAX_VALUE}) {
            mockMvc.perform(get("/api/v1/admin/orders/" + id).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isNotFound()).andExpect(jsonPath("$.data").doesNotExist());
        }
        for (String id : new String[] {"0", "-1", "invalid", "9223372036854775808"}) {
            mockMvc.perform(get("/api/v1/admin/orders/" + id).header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void cancelledOrdersKeepTheirHistoricalInformationReadable() throws Exception {
        jdbc.update("UPDATE work_order SET order_status='CANCELLED', cancel_reason='客户取消' WHERE id=?", orderId);
        JsonNode detail = getData("/api/v1/admin/orders/" + orderId, adminToken);
        assertThat(detail.at("/order/statusCode").asText()).isEqualTo("CANCELLED");
        assertThat(detail.at("/order/cancelReason").asText()).isEqualTo("客户取消");
    }

    private JsonNode getData(String path, String token) throws Exception {
        String response = mockMvc.perform(get(path).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode postData(String path, String token, String body) throws Exception {
        String response = mockMvc.perform(post(path).header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private long upload(String token, String name) throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a,
                0, 0, 0, 0x0d, 0x49, 0x48, 0x44, 0x52};
        String response = mockMvc.perform(multipart("/api/files/upload")
                        .file(new MockMultipartFile("file", name, "image/png", png))
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long createRequest(long targetOrder, String number, String status, String remark, String voidReason) {
        jdbc.update("INSERT INTO material_request(request_no, order_id, installer_user_id, request_status, "
                        + "remark, void_reason, submitted_at) VALUES (?, ?, ?, ?, ?, ?, '2026-09-04 09:00:00')",
                number, targetOrder, installerId, status, remark, voidReason);
        return jdbc.queryForObject("SELECT id FROM material_request WHERE request_no=?", Long.class, number);
    }

    private long createOrder(String number) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus("PENDING_VISIT");
        order.setCustomerUserId(customerId);
        order.setCustomerName("详情客户");
        order.setCustomerPhone("13998000003");
        order.setInstallerUserId(installerId);
        order.setProvinceName("湖北省");
        order.setCityName("武汉市");
        order.setDistrictName("洪山区");
        order.setDetailedAddress("测试路98号");
        order.setRequiredStartAt(LocalDateTime.of(2026, 9, 4, 8, 0));
        order.setExpectedEndAt(LocalDateTime.of(2026, 9, 5, 8, 0));
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy(adminId);
        orderMapper.insert(order);
        return order.getId();
    }

    private long createUser(String name, String phone, RoleCode role) {
        UserEntity user = new UserEntity();
        user.setRealName(name);
        user.setPhone(phone);
        user.setAccountStatus("ENABLED");
        user.setAuditStatus("APPROVED");
        user.setBlacklist(false);
        user.setDeleted(false);
        userMapper.insert(user);
        jdbc.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code=?",
                user.getId(), role.name());
        return user.getId();
    }

    private AuthenticatedUser actor(long id, RoleCode role) {
        return new AuthenticatedUser(id, null, "测试用户", null, Set.of(role));
    }

    private String token(long id, RoleCode role) {
        return jwtService.issue(actor(id, role)).value();
    }
}
