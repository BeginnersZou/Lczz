package com.lczz.servicepage.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.servicepage.service.ServicePageService;
import com.lczz.servicepage.service.ServicePageService.ContentItem;
import com.lczz.servicepage.service.ServicePageService.ServicePageCommand;
import com.lczz.servicepage.service.ServicePageService.ServicePageView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/admin/service-page", "/api/v1/admin/service-page"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台服务管理")
public class AdminServicePageController {
    private final ServicePageService service;

    public AdminServicePageController(ServicePageService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "管理员查询服务页配置")
    ApiResponse<ServicePageView> get(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(service.get(actor), requestId(request));
    }

    @PutMapping
    @Operation(summary = "管理员保存并发布服务页配置")
    ApiResponse<ServicePageView> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @Valid @RequestBody ServicePageRequest body,
                                        HttpServletRequest request) {
        return ApiResponse.success(service.update(actor, body.toCommand()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record ServicePageRequest(
            @NotBlank @Size(max = 100) String companyName,
            @NotBlank @Size(max = 100) String companySubtitle,
            @NotBlank @Size(max = 200) String slogan,
            boolean brandVisible,
            @NotNull @Size(min = 1, max = 6) List<@Valid ContentItemRequest> heroStats,
            boolean servicesVisible,
            @NotNull @Size(min = 1, max = 12) List<@Valid ContentItemRequest> services,
            @NotBlank @Size(max = 2000) String profileText,
            boolean profileVisible,
            @NotNull @Size(max = 12) List<@NotBlank @Size(max = 50) String> profileTags,
            boolean galleryVisible,
            @NotNull @Size(max = 20) List<@NotNull Long> galleryImageFileIds,
            boolean advantagesVisible,
            @NotNull @Size(min = 1, max = 12) List<@Valid ContentItemRequest> advantages,
            boolean contactVisible,
            @NotBlank @Pattern(regexp = "^[0-9+() -]{5,32}$") String phonePrimary,
            @Pattern(regexp = "^$|^[0-9+() -]{5,32}$") String phoneSecondary,
            @NotBlank @Size(max = 500) String address,
            @NotNull @DecimalMin("-180") @DecimalMax("180") BigDecimal longitude,
            @NotNull @DecimalMin("-90") @DecimalMax("90") BigDecimal latitude,
            @NotBlank @Size(max = 255) String businessHours) {
        ServicePageCommand toCommand() {
            return new ServicePageCommand(companyName, companySubtitle, slogan, brandVisible,
                    heroStats.stream().map(ContentItemRequest::toCommand).toList(), servicesVisible,
                    services.stream().map(ContentItemRequest::toCommand).toList(), profileText,
                    profileVisible, profileTags, galleryVisible, galleryImageFileIds,
                    advantagesVisible, advantages.stream().map(ContentItemRequest::toCommand).toList(),
                    contactVisible, phonePrimary, phoneSecondary, address, longitude, latitude, businessHours);
        }
    }

    record ContentItemRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 1000) String description) {
        ContentItem toCommand() { return new ContentItem(title, description); }
    }
}
