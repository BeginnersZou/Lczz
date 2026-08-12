package com.lczz.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.WechatIdentityEntity;
import com.lczz.auth.persistence.WechatIdentityMapper;
import com.lczz.auth.service.AdminBootstrapService;
import com.lczz.auth.wechat.WechatIdentity;
import com.lczz.auth.wechat.WechatIdentityGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTests {
    @Autowired MockMvc mockMvc;
    @Autowired AdminBootstrapService bootstrapService;
    @Autowired UserMapper userMapper;
    @Autowired WechatIdentityMapper identityMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @MockitoBean WechatIdentityGateway wechatGateway;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("DELETE FROM user_wechat_identity");
        jdbcTemplate.update("DELETE FROM sys_user_role");
        jdbcTemplate.update("DELETE FROM sys_user");
    }

    @Test
    void bootstrapAdminCanLoginAndUseTokenForInfo() throws Exception {
        bootstrapService.createIfMissing("admin", "very-secure-123", "管理员");
        String response = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userInfo.role").value("admin"))
                .andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/token").asText();

        mockMvc.perform(get("/api/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void invalidCredentialsHaveExplicitErrorWithoutLeakingAccountState() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"missing\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.error").value("BAD_CREDENTIALS"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void repeatedWechatAndPhoneLoginDoesNotCreateDuplicateUser() throws Exception {
        when(wechatGateway.exchangeLoginCode(anyString()))
                .thenReturn(new WechatIdentity("wx-app", "open-1", "union-1"));
        when(wechatGateway.exchangePhoneCode(anyString())).thenReturn("13800138000");

        bindNewUser("login-code-1", "phone-code-1");
        mockMvc.perform(post("/api/auth/wechat/login").contentType("application/json")
                        .content("{\"code\":\"login-code-2\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needPhone").value(false))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        assertThat(userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, "13800138000"))).isEqualTo(1);
        assertThat(identityMapper.selectCount(new LambdaQueryWrapper<WechatIdentityEntity>()
                .eq(WechatIdentityEntity::getAppId, "wx-app")
                .eq(WechatIdentityEntity::getOpenId, "open-1"))).isEqualTo(1);
    }

    @Test
    void disabledAccountTokenIsRejectedWithoutReturningUserData() throws Exception {
        bootstrapService.createIfMissing("disabled-admin", "very-secure-123", "停用管理员");
        String response = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"disabled-admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/token").asText();
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getUsername, "disabled-admin"));
        user.setAccountStatus("DISABLED");
        userMapper.updateById(user);

        mockMvc.perform(get("/api/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.error").value("ACCOUNT_UNAVAILABLE"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private void bindNewUser(String loginCode, String phoneCode) throws Exception {
        mockMvc.perform(post("/api/auth/wechat/login").contentType("application/json")
                        .content("{\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needPhone").value(true));
        mockMvc.perform(post("/api/auth/wechat/bind-phone").contentType("application/json")
                        .content("{\"code\":\"" + loginCode + "\",\"phoneCode\":\"" + phoneCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userInfo.role").value("customer"));
    }
}
