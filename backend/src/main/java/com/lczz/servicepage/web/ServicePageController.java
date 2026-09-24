package com.lczz.servicepage.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.servicepage.service.ServicePageService;
import com.lczz.servicepage.service.ServicePageService.ServicePageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/service-page", "/api/v1/service-page"})
@Tag(name = "小程序服务页")
public class ServicePageController {
    private final ServicePageService service;

    public ServicePageController(ServicePageService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "公开查询小程序服务页配置")
    ApiResponse<ServicePageView> get(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(service.get(actor), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }
}
