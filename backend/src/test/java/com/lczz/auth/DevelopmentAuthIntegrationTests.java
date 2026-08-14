package com.lczz.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.service.DevelopmentAuthService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "local"})
@TestPropertySource(properties = {
        "lczz.dev-auth.enabled=true",
        "lczz.dev-auth.password=LczzTest@2026"
})
class DevelopmentAuthIntegrationTests {
    private static final String PASSWORD = "LczzTest@2026";
    private static final Map<String, String> ACCOUNTS = Map.of(
            "admin-test", "admin",
            "installer-test", "installer",
            "customer-test", "customer",
            "dealer-test", "dealer");

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired DevelopmentAuthService developmentAuthService;
    @Autowired ObjectMapper objectMapper;

    @BeforeEach
    void resetAccounts() {
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
        developmentAuthService.initializeAccounts();
    }

    @Test
    void allFourTestIdentitiesReceiveRealJwtWithExpectedRole() throws Exception {
        for (Map.Entry<String, String> account : ACCOUNTS.entrySet()) {
            String response = login("/api/v1/auth/dev/login", account.getKey(), PASSWORD)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userInfo.role").value(account.getValue()))
                    .andExpect(jsonPath("$.data.token").isNotEmpty())
                    .andReturn().getResponse().getContentAsString();
            String token = objectMapper.readTree(response).at("/data/token").asText();
            mockMvc.perform(get("/api/auth/info").header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value(account.getKey()))
                    .andExpect(jsonPath("$.data.role").value(account.getValue()));
        }
    }

    @Test
    void testAdministratorAlsoUsesRegularAdminLogin() throws Exception {
        login("/api/auth/login", "admin-test", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userInfo.role").value("admin"));
    }

    @Test
    void wrongPasswordReturnsGenericCredentialError() throws Exception {
        login("/api/auth/dev/login", "installer-test", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private org.springframework.test.web.servlet.ResultActions login(String path, String username, String password)
            throws Exception {
        return mockMvc.perform(post(path).contentType("application/json")
                .content(objectMapper.writeValueAsString(Map.of("username", username, "password", password))));
    }
}
