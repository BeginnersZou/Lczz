package com.lczz.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lczz.auth.persistence.UserEntity;
import com.lczz.auth.persistence.UserMapper;
import com.lczz.auth.persistence.WechatIdentityEntity;
import com.lczz.auth.persistence.WechatIdentityMapper;
import com.lczz.auth.security.JwtService;
import com.lczz.auth.service.AdminBootstrapService;
import com.lczz.auth.service.UserAccountService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
    @Autowired JwtService jwtService;
    @Autowired UserAccountService userAccountService;
    @MockitoBean WechatIdentityGateway wechatGateway;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("DELETE FROM wechat_login_challenge");
        jdbcTemplate.update("DELETE FROM operation_audit_log");
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
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.name").value("管理员"))
                .andExpect(jsonPath("$.data.nickname").value("管理员"))
                .andExpect(jsonPath("$.data.realName").doesNotExist());
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
    void preCreatedInstallerBindsWechatWithoutCreatingDuplicateOrLosingRole() throws Exception {
        bootstrapService.createIfMissing("precreate-admin", "very-secure-123", "预创建管理员");
        String adminResponse = mockMvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"username\":\"precreate-admin\",\"password\":\"very-secure-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String adminToken = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(adminResponse).at("/data/token").asText();

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType("application/json")
                        .content("""
                                {"nickname":"预创建师傅","realName":"王安装","gender":"male",
                                 "phone":"13800138009","role":"installer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("INSTALLER"));

        when(wechatGateway.exchangeLoginCode(anyString()))
                .thenReturn(new WechatIdentity("wx-app", "precreated-open", "precreated-union"));
        when(wechatGateway.exchangePhoneCode(anyString())).thenReturn("13800138009");

        mockMvc.perform(post("/api/auth/wechat/login").contentType("application/json")
                        .content("{\"code\":\"precreated-login-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.needPhone").value(true));
        mockMvc.perform(post("/api/auth/wechat/bind-phone").contentType("application/json")
                        .content("""
                                {"code":"precreated-login-code","phoneCode":"precreated-phone-code"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userInfo.role").value("installer"))
                .andExpect(jsonPath("$.data.userInfo.roles[0]").value("installer"))
                .andExpect(jsonPath("$.data.userInfo.name").value("王安装"))
                .andExpect(jsonPath("$.data.userInfo.nickname").value("预创建师傅"))
                .andExpect(jsonPath("$.data.userInfo.realName").value("王安装"));

        assertThat(userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getPhone, "13800138009"))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT r.role_code FROM sys_user_role ur "
                + "JOIN sys_role r ON r.id=ur.role_id JOIN sys_user u ON u.id=ur.user_id "
                + "WHERE u.phone='13800138009'", String.class)).isEqualTo("INSTALLER");
        assertThat(identityMapper.selectCount(new LambdaQueryWrapper<WechatIdentityEntity>()
                .eq(WechatIdentityEntity::getAppId, "wx-app")
                .eq(WechatIdentityEntity::getOpenId, "precreated-open"))).isEqualTo(1);
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

    @Test
    void customerCanUpdateOnlyOwnNicknameAndRealNameAndReadThemSeparately() throws Exception {
        when(wechatGateway.exchangeLoginCode(anyString()))
                .thenReturn(new WechatIdentity("wx-app", "profile-open", "profile-union"));
        when(wechatGateway.exchangePhoneCode(anyString())).thenReturn("13800138008");
        String token = bindNewUser("profile-login-code", "profile-phone-code");

        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("""
                                {"nickname":"  新昵称  ","realName":"  张三  ",
                                 "phone":"13900000000","role":"installer"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.realName").value("张三"))
                .andExpect(jsonPath("$.data.name").value("新昵称"))
                .andExpect(jsonPath("$.data.phone").value("13800138008"))
                .andExpect(jsonPath("$.data.role").value("customer"));

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("新昵称"))
                .andExpect(jsonPath("$.data.realName").value("张三"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_audit_log WHERE operation_type='SELF_PROFILE_UPDATE'", Long.class))
                .isEqualTo(1);
    }

    @Test
    void profileUpdateValidatesFieldsAndRequiresAuthentication() throws Exception {
        when(wechatGateway.exchangeLoginCode(anyString()))
                .thenReturn(new WechatIdentity("wx-app", "validation-open", "validation-union"));
        when(wechatGateway.exchangePhoneCode(anyString())).thenReturn("13800138006");
        String token = bindNewUser("validation-login-code", "validation-phone-code");

        mockMvc.perform(put("/api/auth/profile").contentType("application/json")
                        .content("{\"nickname\":\"用户\",\"realName\":\"姓名\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"   \",\"realName\":\"姓名\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"" + "字".repeat(65) + "\",\"realName\":null}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"用户\",\"realName\":\"" + "字".repeat(65) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
        mockMvc.perform(put("/api/auth/profile").header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"  " + "字".repeat(64) + "  \",\"realName\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value("字".repeat(64)));
    }

    @Test
    void installerCannotClearOwnRealName() throws Exception {
        UserEntity installer = new UserEntity();
        installer.setNickname("安装师傅");
        installer.setRealName("王安装");
        installer.setPhone("13800138005");
        installer.setAccountStatus("ENABLED");
        installer.setAuditStatus("APPROVED");
        installer.setBlacklist(false);
        installer.setDeleted(false);
        userMapper.insert(installer);
        jdbcTemplate.update("INSERT INTO sys_user_role(user_id, role_id) "
                + "SELECT ?, id FROM sys_role WHERE role_code='INSTALLER'", installer.getId());
        String token = jwtService.issue(userAccountService.requireActive(installer.getId())).value();

        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"nickname\":\"安装师傅\",\"realName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INSTALLER_REAL_NAME_REQUIRED"));
        assertThat(userMapper.selectById(installer.getId()).getRealName()).isEqualTo("王安装");
    }

    @Test
    void customerCanCancelAccountAndRemoveWechatAndPhoneIdentity() throws Exception {
        when(wechatGateway.exchangeLoginCode(anyString()))
                .thenReturn(new WechatIdentity("wx-app", "cancel-open", "cancel-union"));
        when(wechatGateway.exchangePhoneCode(anyString())).thenReturn("13800138007");
        String token = bindNewUser("cancel-login-code", "cancel-phone-code");

        mockMvc.perform(post("/api/auth/account/cancel")
                        .header("Authorization", "Bearer " + token)
                        .contentType("application/json")
                        .content("{\"confirmed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_wechat_identity WHERE open_id='cancel-open'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE phone='13800138007'", Long.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user WHERE nickname='已注销用户' AND deleted=TRUE", Long.class)).isEqualTo(1);
        mockMvc.perform(get("/api/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String bindNewUser(String loginCode, String phoneCode) throws Exception {
        mockMvc.perform(post("/api/auth/wechat/login").contentType("application/json")
                        .content("{\"code\":\"" + loginCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.needPhone").value(true));
        String response = mockMvc.perform(post("/api/auth/wechat/bind-phone").contentType("application/json")
                        .content("{\"code\":\"" + loginCode + "\",\"phoneCode\":\"" + phoneCode + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userInfo.role").value("customer"))
                .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).at("/data/token").asText();
    }
}
