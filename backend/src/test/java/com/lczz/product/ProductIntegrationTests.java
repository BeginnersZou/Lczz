package com.lczz.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.auth.service.AdminBootstrapService;
import com.lczz.auth.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired UserAccountService userAccountService;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM product_category");
        jdbcTemplate.update("DELETE FROM file_asset");
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void administratorCanManageCategoriesProductsImagesAndAvailability() throws Exception {
        String token = adminToken();
        long parentId = createCategory(token, "materials", "安装辅料", null);
        long childId = createCategory(token, "copper", "铜管", parentId);
        jdbcTemplate.update("INSERT INTO file_asset(access_url, deleted) VALUES (?, FALSE)", "/files/cover.jpg");
        Long coverId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_asset", Long.class);
        jdbcTemplate.update("INSERT INTO file_asset(access_url, deleted) VALUES (?, FALSE)", "/files/detail.jpg");
        Long detailId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_asset", Long.class);

        String response = mockMvc.perform(post("/api/v1/consumables")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("""
                                {"productCode":"CU-001","name":"空调铜管","categoryId":%d,
                                 "spec":"φ6×0.8mm","unit":"米","stock":120.5,"price":18.8,
                                 "remark":"一期展示产品","coverFileId":%d,"detailFileIds":[%d],
                                 "enabled":true,"sortOrder":10}
                                """.formatted(childId, coverId, detailId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value("CU-001"))
                .andExpect(jsonPath("$.data.category[0]").value("安装辅料"))
                .andExpect(jsonPath("$.data.category[1]").value("铜管"))
                .andExpect(jsonPath("$.data.image").value("/files/cover.jpg"))
                .andExpect(jsonPath("$.data.detailImages[0].url").value("/files/detail.jpg"))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(patch("/api/v1/consumables/{id}/enabled", productId)
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/v1/consumables/list").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void customerOnlySeesEnabledProductsAndCannotWrite() throws Exception {
        String admin = adminToken();
        long parentId = createCategory(admin, "materials", "安装辅料", null);
        long childId = createCategory(admin, "aux", "其他辅材", parentId);
        long enabledId = insertProduct("P-ON", "可见耗材", childId, true);
        long disabledId = insertProduct("P-OFF", "下架耗材", childId, false);
        String customer = customerToken();

        mockMvc.perform(get("/api/v1/consumables/list")
                        .header("Authorization", bearer(customer)).param("enabled", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].id").value(enabledId));
        mockMvc.perform(get("/api/v1/consumables/detail/{id}", disabledId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/consumables/{id}", enabledId)
                        .header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    void categoryInUseCannotBeDeletedAndPurchaseEndpointDoesNotExist() throws Exception {
        String token = adminToken();
        long parentId = createCategory(token, "materials", "安装辅料", null);
        long childId = createCategory(token, "cable", "电缆", parentId);
        long productId = insertProduct("P-1", "电缆", childId, true);

        mockMvc.perform(delete("/api/v1/consumables/categories/{id}", childId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("CATEGORY_IN_USE"));
        mockMvc.perform(post("/api/v1/consumables/{id}/purchase", productId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("RESOURCE_NOT_FOUND"));
    }

    private long createCategory(String token, String code, String name, Long parentId) throws Exception {
        String parent = parentId == null ? "null" : parentId.toString();
        String response = mockMvc.perform(post("/api/v1/consumables/categories")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name
                                + "\",\"parentId\":" + parent + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/id").asLong();
    }

    private long insertProduct(String code, String name, long categoryId, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO product(product_code, product_name, category_id, unit, enabled, deleted)
                VALUES (?, ?, ?, '件', ?, FALSE)
                """, code, name, categoryId, enabled);
        return jdbcTemplate.queryForObject("SELECT id FROM product WHERE product_code = ?", Long.class, code);
    }

    private String adminToken() throws Exception {
        bootstrapService.createIfMissing("product-admin", "very-secure-123", "产品管理员");
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"product-admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String customerToken() {
        jdbcTemplate.update("""
                INSERT INTO sys_user(nickname, phone, account_status, audit_status, blacklist, deleted)
                VALUES ('客户', '13800138000', 'ENABLED', 'APPROVED', FALSE, FALSE)
                """);
        Long userId = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE phone = '13800138000'", Long.class);
        jdbcTemplate.update("""
                INSERT INTO sys_user_role(user_id, role_id)
                SELECT ?, id FROM sys_role WHERE role_code = 'CUSTOMER'
                """, userId);
        return jwtService.issue(userAccountService.requireActive(userId)).value();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
