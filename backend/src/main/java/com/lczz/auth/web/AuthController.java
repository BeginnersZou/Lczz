package com.lczz.auth.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.auth.service.AuthService;
import com.lczz.auth.service.AuthService.LoginResult;
import com.lczz.auth.service.AuthService.UserInfo;
import com.lczz.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/auth", "/api/v1/auth"})
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ApiResponse<LoginResult> login(@Valid @RequestBody PasswordLoginRequest body, HttpServletRequest request) {
        return ApiResponse.success(authService.passwordLogin(body.username(), body.password(), request.getRemoteAddr()),
                requestId(request));
    }

    @PostMapping("/wechat/login")
    ApiResponse<Map<String, Object>> wechatLogin(@Valid @RequestBody WechatLoginRequest body,
                                                  HttpServletRequest request) {
        var result = authService.wechatLogin(body.code(), request.getRemoteAddr());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("needPhone", result.needPhone());
        if (result.login() != null) putLogin(data, result.login());
        return ApiResponse.success(data, requestId(request));
    }

    @PostMapping("/wechat/bind-phone")
    ApiResponse<LoginResult> bindPhone(@Valid @RequestBody BindPhoneRequest body, HttpServletRequest request) {
        return ApiResponse.success(authService.bindPhone(body.code(), body.phoneCode(), request.getRemoteAddr()),
                requestId(request));
    }

    @GetMapping({"/info", "/me"})
    ApiResponse<UserInfo> info(@AuthenticationPrincipal AuthenticatedUser principal, HttpServletRequest request) {
        return ApiResponse.success(UserInfo.from(authService.current(principal)), requestId(request));
    }

    @PostMapping("/logout")
    ApiResponse<Boolean> logout(HttpServletRequest request) {
        return ApiResponse.success(true, requestId(request));
    }

    private void putLogin(Map<String, Object> data, LoginResult login) {
        data.put("token", login.token());
        data.put("tokenType", login.tokenType());
        data.put("expiresIn", login.expiresIn());
        data.put("userInfo", login.userInfo());
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record PasswordLoginRequest(@NotBlank @Size(max = 64) String username,
                                @NotBlank @Size(max = 128) String password) { }
    record WechatLoginRequest(@NotBlank @Size(max = 256) String code) { }
    record BindPhoneRequest(@NotBlank @Size(max = 256) String code,
                            @NotBlank @Size(max = 256) String phoneCode) { }
}
