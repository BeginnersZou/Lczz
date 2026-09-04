package com.lczz.stocking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.order.persistence.WorkOrderEntity;
import com.lczz.order.persistence.WorkOrderMapper;
import com.lczz.product.persistence.ProductEntity;
import com.lczz.product.persistence.ProductMapper;
import java.math.BigDecimal;
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
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MaterialRequestIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserMapper userMapper;
    @Autowired WorkOrderMapper orderMapper;
    @Autowired ProductMapper productMapper;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RestClient restClient;

    private long adminId;
    private long installerId;
    private long otherInstallerId;
    private long customerId;
    private long orderId;
    private long product1Id;
    private long product2Id;
    private String adminToken;
    private String installerToken;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM material_self_order_item");
        jdbcTemplate.update("DELETE FROM material_self_order");
        jdbcTemplate.update("DELETE FROM installer_cart_item");
        jdbcTemplate.update("DELETE FROM product_sku_spec_value");
        jdbcTemplate.update("DELETE FROM product_sku");
        jdbcTemplate.update("DELETE FROM product_spec_value");
        jdbcTemplate.update("DELETE FROM product_spec_dimension");
        jdbcTemplate.update("DELETE FROM material_request_item");
        jdbcTemplate.update("DELETE FROM material_request");
        jdbcTemplate.update("DELETE FROM work_order_status_history");
        jdbcTemplate.update("DELETE FROM work_order_assignment");
        jdbcTemplate.update("DELETE FROM work_order");
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        adminId = createUser("stock-admin", "管理员", "13910000001", RoleCode.ADMIN);
        installerId = createUser(null, "张师傅", "13910000002", RoleCode.INSTALLER);
        otherInstallerId = createUser(null, "李师傅", "13910000003", RoleCode.INSTALLER);
        customerId = createUser(null, "王客户", "13810000001", RoleCode.CUSTOMER);
        long categoryId = createCategory();
        product1Id = createProduct(categoryId, "MAT-COPPER", "铜管", "φ6", "米", "10.000");
        product2Id = createProduct(categoryId, "MAT-BRACKET", "外机支架", "标准", "套", "5.000");
        orderId = createOrder("WO-STOCK-001", installerId);
        adminToken = token(adminId, RoleCode.ADMIN);
        installerToken = token(installerId, RoleCode.INSTALLER);
    }

    @Test
    void assignedInstallerSubmitsSnapshotsAndIdenticalRetryIsIdempotent() throws Exception {
        jdbcTemplate.update("UPDATE work_order SET order_status='PENDING_VISIT' WHERE id=?", orderId);
        JsonNode first = submit(orderId, installerToken, itemsJson("2", "1"));
        long requestId = first.path("id").asLong();
        assertThat(first.path("materials").get(0).path("name").asText()).isEqualTo("铜管");
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("8");
        assertThat(orderMapper.selectById(orderId).getOrderStatus()).isEqualTo("IN_PROGRESS");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? "
                + "AND to_status='IN_PROGRESS'", Long.class, orderId)).isEqualTo(1L);
        ProductEntity product = productMapper.selectById(product1Id);
        product.setProductName("已改名铜管");
        productMapper.updateById(product);

        JsonNode retry = submit(orderId, installerToken, itemsJson("2", "1"));
        assertThat(retry.path("id").asLong()).isEqualTo(requestId);
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("8");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_request", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM work_order_status_history WHERE order_id=? "
                + "AND to_status='IN_PROGRESS'", Long.class, orderId)).isEqualTo(1L);
        mockMvc.perform(get("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.materials[0].name").value("铜管"));

        mockMvc.perform(post("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content(itemsJson("3", "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ACTIVE_MATERIAL_REQUEST_EXISTS"));
    }

    @Test
    void rejectsUnassignedInstallerAndNonInstallerSubmission() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + token(otherInstallerId, RoleCode.INSTALLER))
                        .contentType("application/json").content(itemsJson("1", "1")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("ORDER_NOT_ASSIGNED"));
        mockMvc.perform(post("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + token(customerId, RoleCode.CUSTOMER))
                        .contentType("application/json").content(itemsJson("1", "1")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void rejectsRequestedQuantityAboveCurrentStock() throws Exception {
        mockMvc.perform(post("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json").content(itemsJson("11", "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_SKU_STOCK"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("库存仅剩10")));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM material_request", Long.class)).isZero();
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("10.000");
    }

    @Test
    void adminTracksPreparationCompletionAndVoidWithReservedStockRelease() throws Exception {
        JsonNode request = submit(orderId, installerToken, itemsJson("2", "1"));
        long requestId = request.path("id").asLong();
        long firstItem = request.path("materials").get(0).path("id").asLong();
        long secondItem = request.path("materials").get(1).path("id").asLong();
        String partial = "{\"materials\":[{\"id\":" + firstItem + ",\"checked\":true},{\"id\":"
                + secondItem + ",\"checked\":false}]}";
        mockMvc.perform(post("/api/preparation/" + requestId + "/prepare")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(partial))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("preparing"));
        mockMvc.perform(post("/api/preparation/" + requestId + "/finish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("MATERIALS_NOT_FULLY_PREPARED"));
        String complete = "{\"materials\":[{\"id\":" + firstItem + ",\"checked\":true},{\"id\":"
                + secondItem + ",\"checked\":true}]}";
        mockMvc.perform(post("/api/preparation/" + requestId + "/prepare")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content(complete))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/preparation/" + requestId + "/finish")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("done"))
                .andExpect(jsonPath("$.data.completedBy").value(adminId))
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty());
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("8.000");

        long secondOrder = createOrder("WO-STOCK-002", installerId);
        JsonNode voidable = submit(secondOrder, installerToken,
                "{\"items\":[{\"productId\":" + product1Id + ",\"quantity\":1}]}" );
        long voidId = voidable.path("id").asLong();
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("7.000");
        mockMvc.perform(post("/api/preparation/" + voidId + "/void")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json").content("{\"reason\":\"订单调整\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("voided"))
                .andExpect(jsonPath("$.data.voidedBy").value(adminId))
                .andExpect(jsonPath("$.data.voidedAt").isNotEmpty());
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("8.000");
        JsonNode resubmitted = submit(secondOrder, installerToken,
                "{\"items\":[{\"productId\":" + product1Id + ",\"quantity\":1}]}" );
        assertThat(resubmitted.path("id").asLong()).isNotEqualTo(voidId);
        assertThat(productMapper.selectById(product1Id).getDisplayStock()).isEqualByComparingTo("7.000");

        mockMvc.perform(get("/api/preparation/list").param("keyword", "铜管").param("status", "pending")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void multiSkuMaterialRequiresConcreteSkuAndOnlyChangesThatSkuStock() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO product_sku(product_id,sku_code,spec_signature,spec_signature_hash,spec_label,unit,stock,
                                        enabled,default_sku,sort_order,version,deleted)
                VALUES (?,?,?,?,?,?,?,TRUE,FALSE,1,0,FALSE)
                """, product1Id, "MAT-COPPER-35", "口径=35", "hash-copper-35", "口径=35", "米", new BigDecimal("4"));
        mockMvc.perform(post("/api/orders/" + orderId + "/materials")
                        .header("Authorization", "Bearer " + installerToken)
                        .contentType("application/json")
                        .content("{\"items\":[{\"productId\":" + product1Id + ",\"quantity\":2}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SKU_REQUIRED"));
        long skuId = jdbcTemplate.queryForObject(
                "SELECT id FROM product_sku WHERE product_id=? AND sku_code='MAT-COPPER-35'", Long.class, product1Id);

        JsonNode request = submit(orderId, installerToken,
                "{\"items\":[{\"productId\":" + product1Id + ",\"skuId\":" + skuId + ",\"quantity\":2}]}");

        assertThat(request.path("materials").get(0).path("skuId").asLong()).isEqualTo(skuId);
        assertThat(request.path("materials").get(0).path("spec").asText()).isEqualTo("口径=35");
        assertThat(jdbcTemplate.queryForObject("SELECT stock FROM product_sku WHERE id=?", BigDecimal.class, skuId))
                .isEqualByComparingTo("2");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stock FROM product_sku WHERE product_id=? AND default_sku=TRUE", BigDecimal.class, product1Id))
                .isEqualByComparingTo("10");
    }

    @Test
    void adminListsFiltersViewsAndExportsUnifiedWAndASources() throws Exception {
        submit(orderId, installerToken, itemsJson("2", "1"));
        jdbcTemplate.update("""
                INSERT INTO product_sku(product_id,sku_code,spec_signature,spec_signature_hash,spec_label,unit,stock,enabled,
                                        default_sku,sort_order,version,deleted)
                VALUES (?,?,?,?,?,?,10,TRUE,FALSE,0,0,FALSE)
                """, product1Id, "PVC-25-2M", "口径=25mm|长度=2米", "test-hash-pvc", "口径：25mm / 长度：2米", "根");
        long skuId = jdbcTemplate.queryForObject("SELECT id FROM product_sku WHERE sku_code='PVC-25-2M'", Long.class);
        jdbcTemplate.update("INSERT INTO material_self_order(order_no,order_name,installer_id,request_token,order_status) "
                + "VALUES ('A202609040001','客户下单',?,'integration-a-order','ORDERED')", installerId);
        long selfOrderId = jdbcTemplate.queryForObject(
                "SELECT id FROM material_self_order WHERE order_no='A202609040001'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO material_self_order_item(self_order_id,sku_id,product_id,product_name_snapshot,
                                                     sku_code_snapshot,spec_snapshot,unit_snapshot,quantity)
                VALUES (?,?,?,?,?,?,?,?)
                """, selfOrderId, skuId, product1Id, "PVC弯头管", "PVC-25-2M", "口径：25mm / 长度：2米", "根", 3);

        mockMvc.perform(get("/api/preparation/list").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.list[*].source", hasItems("W", "A")))
                .andExpect(jsonPath("$.data.list[?(@.source == 'A')].productName").value(hasItems("客户下单")))
                .andExpect(jsonPath("$.data.list[?(@.source == 'A')].itemCount").value(hasItems(1)));
        mockMvc.perform(get("/api/preparation/list").param("source", "A").param("status", "ORDERED")
                        .param("keyword", "25mm").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].orderId").doesNotExist());
        mockMvc.perform(get("/api/preparation/detail/" + selfOrderId).param("source", "A")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value("A202609040001"))
                .andExpect(jsonPath("$.data.productName").value("客户下单"))
                .andExpect(jsonPath("$.data.materials[0].skuCode").value("PVC-25-2M"))
                .andExpect(jsonPath("$.data.materials[0].spec").value("口径：25mm / 长度：2米"));

        var exported = mockMvc.perform(get("/api/preparation/export").param("source", "A")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        assertThat(exported.getHeader("Content-Disposition")).contains("filename*=UTF-8''");
        String csv = new String(exported.getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv).contains("A202609040001", "PVC弯头管", "PVC-25-2M", "口径：25mm / 长度：2米");
        assertThat(csv).doesNotContain("20.00", "13810000001");
        mockMvc.perform(get("/api/preparation/A/" + selfOrderId + "/export")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/preparation/export")
                        .header("Authorization", "Bearer " + installerToken))
                .andExpect(status().isForbidden());
    }

    private JsonNode submit(long targetOrderId, String token, String body) throws Exception {
        String response = mockMvc.perform(post("/api/orders/" + targetOrderId + "/materials")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.requestNo").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String itemsJson(String firstQuantity, String secondQuantity) {
        return "{\"items\":[{\"productId\":" + product1Id + ",\"quantity\":" + firstQuantity
                + "},{\"productId\":" + product2Id + ",\"quantity\":" + secondQuantity + "}],\"remark\":\"施工耗材\"}";
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
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) SELECT ?, id FROM sys_role WHERE role_code = ?",
                user.getId(), role.name());
        return user.getId();
    }

    private long createCategory() {
        jdbcTemplate.update("INSERT INTO product_category(category_code, category_name, category_level, enabled, deleted) "
                + "VALUES ('stock-test', '测试耗材', 1, TRUE, FALSE)");
        return jdbcTemplate.queryForObject("SELECT id FROM product_category WHERE category_code='stock-test'", Long.class);
    }

    private long createProduct(long categoryId, String code, String name, String spec, String unit, String stock) {
        ProductEntity product = new ProductEntity();
        product.setProductCode(code);
        product.setProductName(name);
        product.setCategoryId(categoryId);
        product.setModelSpec(spec);
        product.setUnit(unit);
        product.setDisplayPrice(new BigDecimal("20.00"));
        product.setDisplayStock(new BigDecimal(stock));
        product.setEnabled(true);
        product.setSortOrder(0);
        product.setVersion(0);
        product.setDeleted(false);
        productMapper.insert(product);
        jdbcTemplate.update("""
                INSERT INTO product_sku(product_id,sku_code,spec_signature,spec_signature_hash,spec_label,unit,stock,enabled,
                                        default_sku,sort_order,version,deleted)
                VALUES (?,?,?,?,?,?,?,TRUE,TRUE,0,0,FALSE)
                """, product.getId(), code + "-DEFAULT", "", "empty-" + product.getId(), spec, unit, new BigDecimal(stock));
        return product.getId();
    }

    private long createOrder(String orderNo, long assignedInstallerId) {
        WorkOrderEntity order = new WorkOrderEntity();
        order.setOrderNo(orderNo);
        order.setTaskType("AIR_CONDITIONING_INSTALL");
        order.setOrderStatus("IN_PROGRESS");
        order.setDescription("空调安装备货");
        order.setCustomerUserId(customerId);
        order.setCustomerName("王客户");
        order.setCustomerPhone("13810000001");
        order.setInstallerUserId(assignedInstallerId);
        order.setDetailedAddress("测试地址");
        order.setVersion(0);
        order.setDeleted(false);
        order.setCreatedBy(adminId);
        orderMapper.insert(order);
        return order.getId();
    }

    private String token(long userId, RoleCode role) {
        return jwtService.issue(new AuthenticatedUser(userId, null, "测试用户", null, Set.of(role))).value();
    }
}
