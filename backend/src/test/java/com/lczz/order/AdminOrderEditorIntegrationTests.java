package com.lczz.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:admin_order_editor;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "lczz.file.local-root=target/test-admin-order-editor-storage"
})
class AdminOrderEditorIntegrationTests {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UserMapper users;
    @Autowired WorkOrderMapper orders;
    @Autowired JwtService jwt;
    @MockitoBean RestClient restClient;
    long adminId, installerId, customerId, otherInstallerId, dealerId, orderId, productId, skuId, secondSkuId, requestId, progressId;
    String adminToken, installerToken, customerToken;

    @BeforeEach
    void setup() throws Exception {
        for (String table : new String[] {"business_file_relation", "material_request_item", "material_request", "work_order_review",
                "work_order_progress", "work_order_status_history", "work_order_assignment", "work_order", "operation_audit_log",
                "sms_notification", "file_asset", "material_self_order_item", "material_self_order", "installer_cart_item",
                "product_sku_spec_value", "product_sku", "product_spec_value", "product_spec_dimension", "product", "product_category",
                "user_wechat_identity", "sys_user_role", "sys_user"}) jdbc.update("DELETE FROM " + table);
        adminId = user("编辑管理员", "13915500001", RoleCode.ADMIN);
        installerId = user("原施工师傅", "13915500002", RoleCode.INSTALLER);
        customerId = user("客户", "13915500003", RoleCode.CUSTOMER);
        otherInstallerId = user("其他师傅", "13915500004", RoleCode.INSTALLER);
        dealerId = user("经销商", "13915500005", RoleCode.DEALER);
        adminToken = token(adminId, RoleCode.ADMIN);
        installerToken = token(installerId, RoleCode.INSTALLER);
        customerToken = token(customerId, RoleCode.CUSTOMER);
        jdbc.update("INSERT INTO product_category(category_code,category_name) VALUES ('EDIT155','耗材')");
        long category = jdbc.queryForObject("SELECT id FROM product_category WHERE category_code='EDIT155'", Long.class);
        jdbc.update("INSERT INTO product(product_code,product_name,category_id,unit,display_stock,enabled,deleted) VALUES ('P155','铜管',?,'米',20,TRUE,FALSE)", category);
        productId = jdbc.queryForObject("SELECT id FROM product WHERE product_code='P155'", Long.class);
        skuId = sku("SKU155-6", "6mm");
        secondSkuId = sku("SKU155-8", "8mm");
        orderId = order("WO-EDIT155");
        requestId = postData("/api/v1/orders/" + orderId + "/materials", installerToken,
                "{\"items\":[{\"productId\":" + productId + ",\"skuId\":" + skuId + ",\"quantity\":2}],\"remark\":\"原申请备注\"}")
                .path("id").asLong();
        progressId = postData("/api/v1/orders/" + orderId + "/progress", installerToken,
                "{\"description\":\"原施工说明\"}").path("id").asLong();
        jdbc.update("UPDATE work_order_progress SET submitted_at='2026-10-12 02:36:00' WHERE id=?", progressId);
    }

    @Test
    void editorShowsOnlyActiveRequestAndOriginalBeijingTime() throws Exception {
        jdbc.update("INSERT INTO material_request(request_no,order_id,installer_user_id,request_status,remark) VALUES ('MR-OLD155',?,?,'VOIDED','旧申请')", orderId, installerId);
        JsonNode view = load(orderId);
        assertThat(view.at("/materialRequest/id").asLong()).isEqualTo(requestId);
        assertThat(view.at("/progress/0/submittedAt").asText()).isEqualTo("2026-10-12T10:36:00+08:00");
        assertThat(view.path("revision").asText()).matches("[a-f0-9]{64}");
        assertThat(view.path("editable").asBoolean()).isTrue();
    }

    @Test
    void adminEditorCanSaveThirtyProgressPhotosAndRejectsThirtyFirstAtomically() throws Exception {
        var images = json.createArrayNode();
        for (int index = 0; index < 30; index++) images.add(upload(adminToken));
        ObjectNode command = command(orderId);
        command.putArray("progress").addObject().put("id", progressId).put("description", "30张施工照片")
                .set("fileIds", images);
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/progress/0/images").size()).isEqualTo(30);
        assertThat(saved.at("/progress/0/submittedAt").asText()).isEqualTo("2026-10-12T10:36:00+08:00");

        ObjectNode overflow = command(orderId);
        var thirtyOne = images.deepCopy().add(upload(adminToken));
        overflow.putArray("progress").addObject().put("id", progressId).put("description", "应回滚的修改")
                .set("fileIds", thirtyOne);
        fail(overflow, 400, "FILE_COUNT_LIMIT");
        JsonNode unchanged = load(orderId);
        assertThat(unchanged.at("/progress/0/images").size()).isEqualTo(30);
        assertThat(unchanged.at("/progress/0/description").asText()).isEqualTo("30张施工照片");

        // Saving the full existing list must still work when an administrator edits only its description.
        ObjectNode retained = command(orderId);
        retained.putArray("progress").addObject().put("id", progressId).put("description", "保留全部30张")
                .set("fileIds", images);
        assertThat(save(orderId, retained).at("/progress/0/images").size()).isEqualTo(30);
    }

    @Test
    void basicOnlySavePreservesImagesMaterialsPreparedStateAndSubmissionTime() throws Exception {
        finishPreparation();
        long image = upload(installerToken);
        bind(image, "PROGRESS", progressId, "PROGRESS");
        long relation = jdbc.queryForObject("SELECT id FROM business_file_relation WHERE file_id=?", Long.class, image);
        var submitted = jdbc.queryForObject("SELECT submitted_at FROM material_request WHERE id=?", LocalDateTime.class, requestId);
        ObjectNode command = command(orderId);
        command.set("order", orderBody("修改基础信息"));
        save(orderId, command);
        assertThat(orders.selectById(orderId).getDescription()).isEqualTo("修改基础信息");
        assertThat(jdbc.queryForObject("SELECT request_status FROM material_request WHERE id=?", String.class, requestId)).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM material_request WHERE id=?", LocalDateTime.class, requestId)).isEqualTo(submitted);
        assertThat(jdbc.queryForObject("SELECT id FROM business_file_relation WHERE file_id=?", Long.class, image)).isEqualTo(relation);
        assertThat(stock()).isEqualByComparingTo("8");
    }

    @Test
    void correctionCanRetainHistoricalInstallerWhoIsNoLongerAssignable() throws Exception {
        jdbc.update("UPDATE sys_user SET account_status='DISABLED' WHERE id=?", installerId);
        jdbc.update("UPDATE work_order SET order_status='REVIEWED' WHERE id=?", orderId);
        ObjectNode command = command(orderId);
        command.set("order", orderBody("更正历史订单"));
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/order/statusCode").asText()).isEqualTo("REVIEWED");
        assertThat(orders.selectById(orderId).getInstallerUserId()).isEqualTo(installerId);
        assertThat(count("work_order_assignment")).isZero();
    }

    @Test
    void completedAndReviewedOrdersCanBeCorrectedWithoutChangingConfirmationOrInstaller() throws Exception {
        JsonNode confirmation = postData("/api/v1/orders/" + orderId + "/confirm-completion", customerToken, "{}");
        String confirmedAt = getData("/api/v1/orders/detail/" + orderId, adminToken).path("customerConfirmedAt").asText();
        for (String completed : new String[] {"PENDING_REVIEW", "REVIEWED"}) {
            if ("REVIEWED".equals(completed)) postData("/api/v1/orders/evaluation", customerToken,
                    "{\"orderId\":" + orderId + ",\"score\":5,\"liked\":true,\"content\":\"保留原客户评价\"}");
            long histories = count("work_order_status_history");
            ObjectNode command = command(orderId);
            command.set("order", orderBody("管理员更正" + completed));
            long image = upload(adminToken);
            command.putArray("attachmentFileIds").add(image);
            var entry = command.putArray("progress").addObject();
            entry.put("id", progressId).put("description", "管理员修正施工说明" + completed);
            long progressImage = upload(adminToken);
            entry.putArray("fileIds").add(progressImage);
            JsonNode saved = save(orderId, command);
            assertThat(saved.at("/order/statusCode").asText()).isEqualTo(completed);
            assertThat(saved.at("/order/customerConfirmedAt").asText()).isEqualTo(confirmedAt);
            assertThat(saved.at("/order/customerConfirmedBy").asLong()).isEqualTo(customerId);
            assertThat(saved.at("/progress/0/installerUserId").asLong()).isEqualTo(installerId);
            assertThat(saved.at("/progress/0/submittedAt").asText()).isEqualTo("2026-10-12T10:36:00+08:00");
            assertThat(saved.at("/lastEdit/operatorUserId").asLong()).isEqualTo(adminId);
            assertThat(saved.at("/lastEdit/editedAt").asText()).endsWith("+08:00");
            assertThat(count("work_order_status_history")).isEqualTo(histories);
        }
        assertThat(jdbc.queryForObject("SELECT content FROM work_order_review WHERE order_id=?", String.class, orderId)).isEqualTo("保留原客户评价");
        assertThat(confirmation).isNotNull();
        http.perform(post("/api/v1/orders/" + orderId + "/progress").header("Authorization", "Bearer " + installerToken)
                .contentType("application/json").content("{\"description\":\"封存后师傅新增\"}"))
                .andExpect(status().isConflict());
        http.perform(post("/api/v1/orders/" + orderId + "/materials").header("Authorization", "Bearer " + installerToken)
                .contentType("application/json").content(materialBody("3", "师傅修改")))
                .andExpect(status().isConflict());
    }

    @Test
    void actualListChangeResetsPreparationAndReconcilesReservedStockOnCompletedOrder() throws Exception {
        finishPreparation();
        postData("/api/v1/orders/" + orderId + "/confirm-completion", customerToken, "{}");
        var originalSubmission = jdbc.queryForObject("SELECT submitted_at FROM material_request WHERE id=?", LocalDateTime.class, requestId);
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree(materialBody("4", "改为四米")));
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/order/statusCode").asText()).isEqualTo("PENDING_REVIEW");
        assertThat(saved.at("/materialRequest/statusCode").asText()).isEqualTo("PENDING");
        assertThat(saved.at("/materialRequest/completedBy").isMissingNode() || saved.at("/materialRequest/completedBy").isNull()).isTrue();
        assertThat(saved.at("/materialRequest/materials/0/preparedQuantity").decimalValue()).isEqualByComparingTo("0");
        assertThat(saved.at("/materialRequest/materials/0/checked").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("SELECT installer_user_id FROM material_request WHERE id=?", Long.class, requestId)).isEqualTo(installerId);
        assertThat(jdbc.queryForObject("SELECT submitted_at FROM material_request WHERE id=?", LocalDateTime.class, requestId)).isEqualTo(originalSubmission);
        assertThat(stock()).isEqualByComparingTo("6");
        assertThat(jdbc.queryForObject("SELECT display_stock FROM product WHERE id=?", BigDecimal.class, productId)).isEqualByComparingTo("16");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE operation_type='ORDER_MATERIAL_STOCK_CHANGE'", Long.class)).isEqualTo(1);
        assertThat(getData("/api/v1/orders/" + orderId + "/materials", installerToken).at("/materials/0/count").asInt()).isEqualTo(4);
        assertThat(getData("/api/v1/preparation/detail/" + requestId, adminToken).path("statusCode").asText()).isEqualTo("PENDING");
        save(orderId, command);
        assertThat(stock()).isEqualByComparingTo("6");
        assertThat(count("material_request")).isEqualTo(1);
    }

    @Test
    void remarkOnlyAndEquivalentListKeepDoneStateAndDoNotReserveAgain() throws Exception {
        finishPreparation();
        long itemId = jdbc.queryForObject("SELECT id FROM material_request_item WHERE request_id=?", Long.class, requestId);
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree(materialBody("2", "仅改备注")));
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/materialRequest/statusCode").asText()).isEqualTo("DONE");
        assertThat(saved.at("/materialRequest/materials/0/id").asLong()).isEqualTo(itemId);
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE operation_type='ORDER_MATERIAL_STOCK_CHANGE'", Long.class)).isZero();
        save(orderId, command);
        assertThat(count("operation_audit_log")).isEqualTo(1);
    }

    @Test
    void replacementCanUseItsOwnReservationEvenWhenAvailableStockIsLow() throws Exception {
        jdbc.update("UPDATE product_sku SET stock=1 WHERE id=?", skuId);
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree(materialBody("3", "库存加预占")));
        save(orderId, command);
        assertThat(stock()).isEqualByComparingTo("0");
    }

    @Test
    void changingSkuReleasesOldSkuAndReservesSpecificNewSku() throws Exception {
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree("{\"items\":[{\"productId\":" + productId + ",\"skuId\":" + secondSkuId + ",\"quantity\":3}],\"remark\":\"换规格\"}"));
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/materialRequest/materials/0/skuId").asLong()).isEqualTo(secondSkuId);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?", BigDecimal.class, secondSkuId)).isEqualByComparingTo("7");
        assertThat(jdbc.queryForObject("SELECT display_stock FROM product WHERE id=?", BigDecimal.class, productId)).isEqualByComparingTo("17");
    }

    @Test
    void reorderingEquivalentSkuListDoesNotResetDonePreparation() throws Exception {
        String first = "{\"productId\":" + productId + ",\"skuId\":" + skuId + ",\"quantity\":2}";
        String second = "{\"productId\":" + productId + ",\"skuId\":" + secondSkuId + ",\"quantity\":1}";
        postData("/api/v1/orders/" + orderId + "/materials", installerToken, "{\"items\":[" + first + "," + second + "]}");
        finishPreparation();
        var oldIds = jdbc.queryForList("SELECT id FROM material_request_item WHERE request_id=? ORDER BY id", Long.class, requestId);
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree("{\"items\":[" + second + "," + first + "]}"));
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/materialRequest/statusCode").asText()).isEqualTo("DONE");
        assertThat(jdbc.queryForList("SELECT id FROM material_request_item WHERE request_id=? ORDER BY id", Long.class, requestId)).isEqualTo(oldIds);
        assertThat(stock()).isEqualByComparingTo("8");
    }

    @Test
    void clearingListVoidsCurrentRequestAndReturnsReservationOnce() throws Exception {
        finishPreparation();
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree("{\"items\":[],\"remark\":\"不需耗材\"}"));
        save(orderId, command);
        assertThat(stock()).isEqualByComparingTo("10");
        assertThat(jdbc.queryForObject("SELECT request_status FROM material_request WHERE id=?", String.class, requestId)).isEqualTo("VOIDED");
        assertThat(load(orderId).path("materialRequest").isMissingNode() || load(orderId).path("materialRequest").isNull()).isTrue();
        save(orderId, command);
        assertThat(stock()).isEqualByComparingTo("10");
    }

    @Test
    void adminCanAddFirstRequestWithoutPretendingToBeInstallerAndMaterialsAreOptional() throws Exception {
        long freshOrder = order("WO-FRESH155");
        ObjectNode basicOnly = command(freshOrder);
        basicOnly.set("order", orderBody("无需耗材"));
        save(freshOrder, basicOnly);
        assertThat(count("material_request")).isEqualTo(1);
        ObjectNode command = command(freshOrder);
        command.set("materials", json.readTree(materialBody("1", "管理员添加")));
        save(freshOrder, command);
        assertThat(jdbc.queryForObject("SELECT installer_user_id FROM material_request WHERE order_id=?", Long.class, freshOrder)).isEqualTo(installerId);
        assertThat(stock()).isEqualByComparingTo("7");
    }

    @Test
    void stockFailureRollsBackBasicProgressImageChangesAndAuditTogether() throws Exception {
        long image = upload(adminToken);
        ObjectNode command = command(orderId);
        command.set("order", orderBody("不能留下部分保存"));
        command.putArray("attachmentFileIds").add(image);
        command.putArray("progress").addObject().put("id", progressId).put("description", "不能留下施工修改");
        command.set("materials", json.readTree(materialBody("11", "库存不足")));
        http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(command.toString()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("INSUFFICIENT_SKU_STOCK"));
        assertThat(orders.selectById(orderId).getDescription()).isEqualTo("原描述");
        assertThat(jdbc.queryForObject("SELECT description FROM work_order_progress WHERE id=?", String.class, progressId)).isEqualTo("原施工说明");
        assertThat(count("business_file_relation")).isZero();
        assertThat(count("operation_audit_log")).isZero();
        assertThat(stock()).isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("SELECT requested_quantity FROM material_request_item WHERE request_id=?", BigDecimal.class, requestId)).isEqualByComparingTo("2");
    }

    @Test
    void replacingAndReorderingImagesKeepsOtherBusinessReferencesAndOriginalAssets() throws Exception {
        long old = upload(installerToken), second = upload(installerToken), added = upload(adminToken);
        bind(old, "PROGRESS", progressId, "PROGRESS");
        bind(second, "PROGRESS", progressId, "PROGRESS");
        long anotherOrder = order("WO-SHARED155");
        bind(old, "ORDER", anotherOrder, "ATTACHMENT");
        ObjectNode command = command(orderId);
        command.putArray("progress").addObject().put("id", progressId).put("description", "调整图片顺序")
                .putArray("fileIds").add(added).add(second);
        JsonNode saved = save(orderId, command);
        assertThat(saved.at("/progress/0/images/0/id").asLong()).isEqualTo(added);
        assertThat(saved.at("/progress/0/images/1/id").asLong()).isEqualTo(second);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_file_relation WHERE file_id=? AND business_id=? AND business_type='ORDER'", Long.class, old, anotherOrder)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT deleted FROM file_asset WHERE id=?", Boolean.class, old)).isFalse();
        assertThat(getData("/api/v1/orders/" + orderId + "/progress", installerToken).at("/0/images/0/id").asLong()).isEqualTo(added);
    }

    @Test
    void rejectsCrossOrderRecordsBorrowedFilesAndOtherUploadersWithoutPartialWrites() throws Exception {
        long otherOrder = order("WO-FOREIGN155");
        long otherProgress = postData("/api/v1/orders/" + otherOrder + "/progress", installerToken, "{\"description\":\"其它记录\"}").path("id").asLong();
        ObjectNode command = command(orderId);
        command.putArray("progress").addObject().put("id", otherProgress).put("description", "越界");
        fail(command, 400, "PROGRESS_ORDER_MISMATCH");
        long borrowed = upload(adminToken);
        bind(borrowed, "ORDER", otherOrder, "ATTACHMENT");
        command = command(orderId);
        command.putArray("attachmentFileIds").add(borrowed);
        fail(command, 403, "EDITOR_FILE_FORBIDDEN");
        command = command(orderId);
        command.putArray("attachmentFileIds").add(upload(installerToken));
        fail(command, 403, "EDITOR_FILE_FORBIDDEN");
        assertThat(count("operation_audit_log")).isZero();
    }

    @Test
    void invalidDuplicateAndExcessImagesCannotModifyOtherSections() throws Exception {
        long file = upload(adminToken);
        ObjectNode command = command(orderId);
        command.set("order", orderBody("不应保存"));
        command.putArray("attachmentFileIds").add(file).add(file);
        fail(command, 400, "INVALID_EDITOR_FILES");
        command = command(orderId);
        var ids = command.putArray("attachmentFileIds");
        for (int i = 0; i < 10; i++) ids.add(file + i);
        http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(command.toString())).andExpect(status().isBadRequest());
        assertThat(orders.selectById(orderId).getDescription()).isEqualTo("原描述");
    }

    @Test
    void staleRevisionsDetectPreparationProgressAndAttachmentChanges() throws Exception {
        ObjectNode stale = command(orderId);
        finishPreparation();
        fail(stale, 409, "ORDER_EDIT_CONFLICT");
        stale = command(orderId);
        postData("/api/v1/orders/" + orderId + "/progress", installerToken, "{\"description\":\"新增施工\"}");
        fail(stale, 409, "ORDER_EDIT_CONFLICT");
        stale = command(orderId);
        bind(upload(adminToken), "ORDER", orderId, "ATTACHMENT");
        fail(stale, 409, "ORDER_EDIT_CONFLICT");
    }

    @Test
    void changedBodyCannotReuseSuccessfulSubmissionId() throws Exception {
        ObjectNode command = command(orderId);
        save(orderId, command);
        command.set("order", orderBody("复用提交号覆盖"));
        fail(command, 409, "EDIT_REQUEST_REUSED");
    }

    @Test
    void simultaneousRetryOfSameSaveReservesOnlyOnce() throws Exception {
        ObjectNode command = command(orderId);
        command.set("materials", json.readTree(materialBody("4", "并发重试")));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var calls = List.of(pool.submit(() -> concurrentSave(command, start)), pool.submit(() -> concurrentSave(command, start)));
            start.countDown();
            assertThat(calls.get(0).get(10, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(calls.get(1).get(10, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(stock()).isEqualByComparingTo("6");
        assertThat(count("material_request")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE operation_type='ORDER_ADMIN_EDIT'", Long.class)).isEqualTo(1);
    }

    @Test
    void simultaneousDifferentEditsCannotOverwriteOneAnother() throws Exception {
        ObjectNode first = command(orderId);
        ObjectNode second = first.deepCopy().put("requestId", UUID.randomUUID().toString());
        first.set("materials", json.readTree(materialBody("3", "并发修改一")));
        second.set("materials", json.readTree(materialBody("4", "并发修改二")));
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> concurrentSave(first, start));
            var two = pool.submit(() -> concurrentSave(second, start));
            start.countDown();
            assertThat(List.of(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(count("material_request")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audit_log WHERE operation_type='ORDER_ADMIN_EDIT'", Long.class)).isEqualTo(1);
    }

    private int concurrentSave(ObjectNode command, CountDownLatch start) throws Exception {
        start.await(5, TimeUnit.SECONDS);
        return http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(command.toString())).andReturn().getResponse().getStatus();
    }

    @Test
    void cancelledOrderIsReadOnlyForEveryEditorSection() throws Exception {
        jdbc.update("UPDATE work_order SET order_status='CANCELLED' WHERE id=?", orderId);
        assertThat(load(orderId).path("editable").asBoolean()).isFalse();
        for (String section : new String[] {"order", "progress", "materials", "attachmentFileIds"}) {
            ObjectNode command = command(orderId);
            if (section.equals("order")) command.set(section, orderBody("作废不可改"));
            else if (section.equals("materials")) command.set(section, json.readTree(materialBody("3", "不可改")));
            else command.putArray(section);
            fail(command, 409, "ORDER_NOT_EDITABLE");
        }
    }

    @Test
    void nonAdminRolesAndAnonymousCannotReadOrSaveEditor() throws Exception {
        ObjectNode body = command(orderId);
        for (String caller : new String[] {installerToken, customerToken, token(dealerId, RoleCode.DEALER), token(otherInstallerId, RoleCode.INSTALLER)}) {
            http.perform(get(editorPath(orderId)).header("Authorization", "Bearer " + caller)).andExpect(status().isForbidden());
            http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + caller)
                    .contentType("application/json").content(body.toString())).andExpect(status().isForbidden());
        }
        http.perform(get(editorPath(orderId))).andExpect(status().isUnauthorized());
        http.perform(put(editorPath(orderId)).contentType("application/json").content(body.toString())).andExpect(status().isUnauthorized());
    }

    @Test
    void validatesQuantitiesDuplicateSkuAndUnavailableProductsOnServer() throws Exception {
        for (String quantity : new String[] {"0", "-1", "1.5", "1000000000"}) {
            ObjectNode command = command(orderId);
            command.set("materials", json.readTree(materialBody(quantity, "错误数量")));
            http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                    .contentType("application/json").content(command.toString())).andExpect(status().isBadRequest());
        }
        ObjectNode command = command(orderId);
        JsonNode material = json.readTree(materialBody("3", "重复"));
        ((com.fasterxml.jackson.databind.node.ArrayNode) material.path("items")).add(material.path("items").get(0).deepCopy());
        command.set("materials", material);
        fail(command, 400, "DUPLICATE_MATERIAL_SKU");
        jdbc.update("UPDATE product_sku SET enabled=FALSE WHERE id=?", skuId);
        command = command(orderId);
        command.set("materials", json.readTree(materialBody("3", "已停用")));
        http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(command.toString())).andExpect(status().isBadRequest());
        assertThat(stock()).isEqualByComparingTo("8");
    }

    @Test
    void missingDeletedAndInvalidOrdersAreRejected() throws Exception {
        http.perform(get(editorPath(Long.MAX_VALUE)).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
        jdbc.update("UPDATE work_order SET deleted=TRUE WHERE id=?", orderId);
        http.perform(get(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)).andExpect(status().isNotFound());
        http.perform(get(editorPath(0)).header("Authorization", "Bearer " + adminToken)).andExpect(status().isBadRequest());
    }

    private void finishPreparation() throws Exception {
        ObjectNode body = json.createObjectNode();
        var items = body.putArray("materials");
        jdbc.queryForList("SELECT id FROM material_request_item WHERE request_id=? ORDER BY id", Long.class, requestId)
                .forEach(id -> items.addObject().put("id", id).put("checked", true));
        postData("/api/v1/preparation/" + requestId + "/prepare", adminToken, body.toString());
        postData("/api/v1/preparation/" + requestId + "/finish", adminToken, "{}");
    }
    private void fail(ObjectNode command, int expected, String error) throws Exception {
        http.perform(put(editorPath(orderId)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(command.toString())).andExpect(status().is(expected)).andExpect(jsonPath("$.error").value(error));
    }
    private JsonNode load(long id) throws Exception { return getData(editorPath(id), adminToken); }
    private ObjectNode command(long id) throws Exception {
        return json.createObjectNode().put("requestId", UUID.randomUUID().toString()).put("revision", load(id).path("revision").asText());
    }
    private JsonNode save(long id, ObjectNode body) throws Exception {
        return json.readTree(http.perform(put(editorPath(id)).header("Authorization", "Bearer " + adminToken)
                .contentType("application/json").content(body.toString())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private ObjectNode orderBody(String description) {
        ObjectNode body = json.createObjectNode().put("taskType", "空调安装").put("description", description)
                .put("customerName", "客户").put("customerPhone", "13915500003").put("addressDetail", "原地址")
                .put("orderStartTime", "2026-10-12T10:00:00").put("orderEndTime", "2026-10-12T11:00:00");
        body.putArray("addressArea").add("湖北省").add("武汉市").add("洪山区");
        body.putArray("masterIds").add(installerId);
        return body;
    }
    private String materialBody(String quantity, String remark) {
        return "{\"items\":[{\"productId\":" + productId + ",\"skuId\":" + skuId + ",\"quantity\":" + quantity + "}],\"remark\":\"" + remark + "\"}";
    }
    private long upload(String caller) throws Exception {
        byte[] png = new byte[] {(byte) 0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a,0,0,0,0x0d,0x49,0x48,0x44,0x52};
        return json.readTree(http.perform(multipart("/api/v1/files/upload").file(new MockMultipartFile("file", "site.png", "image/png", png))
                .header("Authorization", "Bearer " + caller)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/id").asLong();
    }
    private void bind(long file, String type, long business, String usage) throws Exception {
        postData("/api/v1/files/" + file + "/relations", adminToken,
                "{\"businessType\":\"" + type + "\",\"businessId\":" + business + ",\"usageType\":\"" + usage + "\"}");
    }
    private JsonNode getData(String path, String caller) throws Exception {
        return json.readTree(http.perform(get(path).header("Authorization", "Bearer " + caller))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private JsonNode postData(String path, String caller, String body) throws Exception {
        return json.readTree(http.perform(post(path).header("Authorization", "Bearer " + caller).contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
    }
    private long user(String name, String phone, RoleCode role) {
        UserEntity user = new UserEntity();
        user.setRealName(name); user.setPhone(phone); user.setAccountStatus("ENABLED"); user.setAuditStatus("APPROVED"); user.setBlacklist(false); user.setDeleted(false);
        users.insert(user);
        jdbc.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code=?", user.getId(), role.name());
        return user.getId();
    }
    private long order(String number) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(number); order.setTaskType("AIR_CONDITIONING_INSTALL"); order.setOrderStatus("PENDING_VISIT"); order.setCustomerName("客户");
        order.setCustomerPhone("13915500003"); order.setCustomerUserId(customerId); order.setInstallerUserId(installerId); order.setDescription("原描述");
        order.setProvinceName("湖北省"); order.setProvinceCode("湖北省"); order.setCityName("武汉市"); order.setCityCode("武汉市");
        order.setDistrictName("洪山区"); order.setDistrictCode("洪山区"); order.setDetailedAddress("原地址");
        order.setRequiredStartAt(LocalDateTime.of(2026,10,12,10,0)); order.setExpectedEndAt(LocalDateTime.of(2026,10,12,11,0));
        order.setVersion(0); order.setDeleted(false); order.setCreatedBy(adminId); orders.insert(order);
        return order.getId();
    }
    private long sku(String code, String spec) {
        jdbc.update("INSERT INTO product_sku(product_id,sku_code,spec_signature,spec_signature_hash,spec_label,unit,stock,enabled,default_sku,deleted) VALUES (?,?,'',?,?,'米',10,TRUE,FALSE,FALSE)", productId, code, code, spec);
        return jdbc.queryForObject("SELECT id FROM product_sku WHERE sku_code=?", Long.class, code);
    }
    private String token(long id, RoleCode role) { return jwt.issue(new AuthenticatedUser(id, null, "测试用户", null, Set.of(role))).value(); }
    private String editorPath(long id) { return "/api/v1/admin/orders/" + id + "/editor"; }
    private BigDecimal stock() { return jdbc.queryForObject("SELECT stock FROM product_sku WHERE id=?", BigDecimal.class, skuId); }
    private long count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class); }
}
