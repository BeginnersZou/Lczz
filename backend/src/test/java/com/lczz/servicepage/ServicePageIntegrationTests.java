package com.lczz.servicepage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.domain.RoleCode;
import com.lczz.auth.security.JwtService;
import com.lczz.auth.service.AdminBootstrapService;
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

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ServicePageIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtService jwtService;
    @MockitoBean RestClient restClient;

    @BeforeEach
    void resetGallery() {
        jdbcTemplate.update("DELETE FROM business_file_relation WHERE business_type='SERVICE_PAGE'");
        jdbcTemplate.update("DELETE FROM file_asset WHERE object_key LIKE 'service-test/%'");
        jdbcTemplate.update("UPDATE service_page_config SET company_name='武汉力创之尊', company_subtitle='制冷技术服务有限公司', "
                + "slogan='以诚信之心，立潮流之品', profile_text='默认简介', phone_primary='027-82710326', "
                + "phone_secondary='027-82710380', address='默认地址', longitude=114.306997, latitude=30.665673, "
                + "business_hours='周一至周日 8:00-20:00', brand_visible=TRUE, services_visible=TRUE, "
                + "profile_visible=TRUE, gallery_visible=TRUE, advantages_visible=TRUE, contact_visible=TRUE WHERE id=1");
        jdbcTemplate.update("DELETE FROM service_page_item WHERE service_page_id=1");
        jdbcTemplate.update("INSERT INTO service_page_item(service_page_id,item_type,title,description,sort_order) VALUES "
                + "(1,'HERO_STAT','一站式','暖通服务',0),"
                + "(1,'SERVICE','服务一','说明一',0),(1,'SERVICE','服务二','说明二',1),(1,'SERVICE','服务三','说明三',2),"
                + "(1,'PROFILE_TAG','品质保障',NULL,0),(1,'ADVANTAGE','快速响应','及时处理',0)");
    }

    @Test
    void guestsReadSeededContentAndOnlyAdministratorsCanManageIt() throws Exception {
        mockMvc.perform(get("/api/v1/service-page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("武汉力创之尊"))
                .andExpect(jsonPath("$.data.services.length()").value(3))
                .andExpect(jsonPath("$.data.galleryImages.length()").value(0));

        mockMvc.perform(get("/api/v1/admin/service-page"))
                .andExpect(status().isUnauthorized());

        long customerId = createCustomer();
        String customerToken = jwtService.issue(new AuthenticatedUser(customerId, null, "普通用户", null,
                Set.of(RoleCode.CUSTOMER))).value();
        mockMvc.perform(get("/api/v1/admin/service-page")
                        .header("Authorization", bearer(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorPublishesContentAndOrderedGalleryForGuests() throws Exception {
        long second = insertFile("second.jpg", "image/jpeg", 2048);
        long first = insertFile("first.png", "image/png", 1024);
        String payload = payload("新的服务标题", "[" + second + "," + first + "]");

        mockMvc.perform(put("/api/v1/admin/service-page")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json").content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("新的服务标题"))
                .andExpect(jsonPath("$.data.galleryImages[0].id").value(second))
                .andExpect(jsonPath("$.data.galleryImages[1].id").value(first));

        mockMvc.perform(get("/api/v1/service-page"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyName").value("新的服务标题"))
                .andExpect(jsonPath("$.data.galleryImages[0].url", startsWith("/api/files/access/")))
                .andExpect(jsonPath("$.data.address").value("湖北省武汉市江岸区测试地址"));
    }

    @Test
    void rejectsOversizedAndNonImageGalleryFiles() throws Exception {
        String token = adminToken();
        long oversized = insertFile("oversized.jpg", "image/jpeg", 10L * 1024 * 1024 + 1);
        mockMvc.perform(put("/api/v1/admin/service-page")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content(payload("超大图片测试", "[" + oversized + "]")))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("SERVICE_IMAGE_TOO_LARGE"));

        long video = insertFile("video.mp4", "video/mp4", 1024);
        mockMvc.perform(put("/api/v1/admin/service-page")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content(payload("视频测试", "[" + video + "]")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("SERVICE_IMAGE_TYPE_INVALID"));
    }

    private String payload(String companyName, String fileIds) {
        return """
                {
                  "companyName":"%s","companySubtitle":"制冷技术服务有限公司","slogan":"诚信服务",
                  "brandVisible":true,"heroStats":[{"title":"一站式","description":"服务体验"}],
                  "servicesVisible":true,"services":[{"title":"空调安装","description":"专业安装"}],
                  "profileText":"公司简介内容","profileVisible":true,"profileTags":["品质保障"],
                  "galleryVisible":true,"galleryImageFileIds":%s,
                  "advantagesVisible":true,"advantages":[{"title":"快速响应","description":"及时处理"}],
                  "contactVisible":true,"phonePrimary":"027-82710326","phoneSecondary":"027-82710380",
                  "address":"湖北省武汉市江岸区测试地址","longitude":114.306997,"latitude":30.665673,
                  "businessHours":"周一至周日 8:00-20:00"
                }
                """.formatted(companyName, fileIds);
    }

    private long insertFile(String name, String mimeType, long size) {
        jdbcTemplate.update("INSERT INTO file_asset(storage_type,object_key,original_name,mime_type,file_size,deleted) "
                + "VALUES ('LOCAL', ?, ?, ?, ?, FALSE)", "service-test/" + name, name, mimeType, size);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_asset", Long.class);
    }

    private long createCustomer() {
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id IN (SELECT id FROM sys_user WHERE phone='13870000145')");
        jdbcTemplate.update("DELETE FROM sys_user WHERE phone='13870000145'");
        jdbcTemplate.update("INSERT INTO sys_user(nickname,real_name,gender,phone,account_status,audit_status,blacklist,deleted) "
                + "VALUES ('服务页访客','服务页访客','UNKNOWN','13870000145','ENABLED','APPROVED',FALSE,FALSE)");
        long id = jdbcTemplate.queryForObject("SELECT id FROM sys_user WHERE phone='13870000145'", Long.class);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id,role_id) SELECT ?,id FROM sys_role WHERE role_code='CUSTOMER'", id);
        return id;
    }

    private String adminToken() throws Exception {
        bootstrapService.createIfMissing("service-admin", "very-secure-145", "服务管理员");
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"service-admin\",\"password\":\"very-secure-145\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }
}
