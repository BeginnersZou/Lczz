package com.lczz.region;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.service.AdminBootstrapService;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RegionIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AdminBootstrapService bootstrapService;

    @Test
    void returnsCompleteThreeLevelTreeOnBothApiPrefixes() throws Exception {
        String token = adminToken();
        String response = mockMvc.perform(get("/api/v1/regions/tree")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(34))
                .andReturn().getResponse().getContentAsString();
        JsonNode provinces = objectMapper.readTree(response).path("data");
        JsonNode anhui = StreamSupport.stream(provinces.spliterator(), false)
                .filter(node -> "安徽省".equals(node.path("name").asText())).findFirst().orElseThrow();
        JsonNode hefei = StreamSupport.stream(anhui.path("children").spliterator(), false)
                .filter(node -> "合肥市".equals(node.path("name").asText())).findFirst().orElseThrow();
        assertThat(StreamSupport.stream(hefei.path("children").spliterator(), false)
                .anyMatch(node -> "蜀山区".equals(node.path("name").asText()))).isTrue();
        assertThat(StreamSupport.stream(provinces.spliterator(), false)
                .flatMap(province -> StreamSupport.stream(province.path("children").spliterator(), false))
                .allMatch(city -> city.path("children").size() > 0)).isTrue();

        mockMvc.perform(get("/api/regions/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(34));
        mockMvc.perform(get("/api/v1/regions/tree")).andExpect(status().isUnauthorized());
    }

    private String adminToken() throws Exception {
        bootstrapService.createIfMissing("region-admin", "very-secure-123", "区域管理员");
        String response = mockMvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\"region-admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).at("/data/token").asText();
    }
}
