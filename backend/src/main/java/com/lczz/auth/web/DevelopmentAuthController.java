package com.lczz.auth.web;

import com.lczz.auth.service.AuthService.LoginResult;
import com.lczz.auth.service.DevelopmentAuthService;
import com.lczz.common.api.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local")
@ConditionalOnProperty(prefix = "lczz.dev-auth", name = "enabled", havingValue = "true")
@RequestMapping({"/api/auth/dev", "/api/v1/auth/dev"})
public class DevelopmentAuthController {
    private final DevelopmentAuthService service;

    public DevelopmentAuthController(DevelopmentAuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    ApiResponse<LoginResult> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.login(body.username(), body.password()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record LoginRequest(@NotBlank @Size(max = 64) String username,
                        @NotBlank @Size(max = 128) String password) { }
}
