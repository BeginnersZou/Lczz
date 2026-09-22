package com.lczz.projectcase;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.service.AdminBootstrapService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProjectCaseIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean RestClient restClient;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM business_file_relation");
        jdbcTemplate.update("DELETE FROM project_case");
        jdbcTemplate.update("DELETE FROM file_asset");
    }

    @Test
    void administratorCanManageCasesAndGuestsCanReadThem() throws Exception {
        String token = adminToken();
        long coverId = insertImage("case-cover.jpg", 1024);
        long detailId = insertImage("case-detail.jpg", 2048);

        String response = mockMvc.perform(post("/api/v1/cases")
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"siteName\":\"武汉江岸区示范工地\",\"imageFileIds\":[" + coverId + "," + detailId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("武汉江岸区示范工地"))
                .andExpect(jsonPath("$.data.images.length()").value(2))
                .andReturn().getResponse().getContentAsString();
        long caseId = objectMapper.readTree(response).at("/data/id").asLong();

        mockMvc.perform(get("/api/v1/cases/list").param("keyword", "江岸区"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.list[0].imageCount").value(2))
                .andExpect(jsonPath("$.data.list[0].coverImage.url")
                        .value(org.hamcrest.Matchers.startsWith("/api/files/access/")));
        mockMvc.perform(get("/api/v1/cases/{id}", caseId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.images.length()").value(2));

        mockMvc.perform(put("/api/v1/cases/{id}", caseId)
                        .header("Authorization", bearer(token)).contentType("application/json")
                        .content("{\"siteName\":\"武汉天地施工案例\",\"imageFileIds\":[" + detailId + "]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.siteName").value("武汉天地施工案例"))
                .andExpect(jsonPath("$.data.images.length()").value(1));
        mockMvc.perform(get("/api/v1/cases/list").param("keyword", "天地"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.list[0].imageCount").value(1));
        mockMvc.perform(get("/api/v1/cases/list").param("keyword", "不存在"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(delete("/api/v1/cases/{id}", caseId).header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/cases/{id}", caseId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PROJECT_CASE_NOT_FOUND"));
        org.assertj.core.api.Assertions.assertThat(jdbcTemplate.queryForObject(
                "SELECT deleted FROM file_asset WHERE id=?", Boolean.class, detailId)).isTrue();
    }

    @Test
    void rejectsMissingOversizedAndNonImageCaseFiles() throws Exception {
        String token = adminToken();
        long oversizedId = insertImage("oversized.jpg", 10L * 1024 * 1024 + 1);
        mockMvc.perform(post("/api/v1/cases").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"siteName\":\"超大图片案例\",\"imageFileIds\":[" + oversizedId + "]}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value("CASE_IMAGE_TOO_LARGE"));

        long videoId = insertFile("case-video.mp4", "video/mp4", 1024);
        mockMvc.perform(post("/api/v1/cases").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"siteName\":\"视频案例\",\"imageFileIds\":[" + videoId + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CASE_IMAGE_TYPE_INVALID"));

        mockMvc.perform(post("/api/v1/cases").contentType("application/json")
                        .content("{\"siteName\":\"未登录案例\",\"imageFileIds\":[" + oversizedId + "]}"))
                .andExpect(status().isUnauthorized());
    }

    private long insertImage(String name, long size) {
        return insertFile(name, "image/jpeg", size);
    }

    private long insertFile(String name, String mimeType, long size) {
        jdbcTemplate.update("INSERT INTO file_asset(storage_type,object_key,original_name,mime_type,file_size,deleted) "
                        + "VALUES ('LOCAL', ?, ?, ?, ?, FALSE)", "cases/" + name, name, mimeType, size);
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM file_asset", Long.class);
    }

    private String adminToken() throws Exception {
        bootstrapService.createIfMissing("case-admin", "very-secure-123", "案例管理员");
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"case-admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
