package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.AdminOrderDetailService;
import com.lczz.order.service.AdminOrderDetailService.ProjectManagerOrderDetailView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/project-manager/orders", "/api/v1/project-manager/orders"})
@PreAuthorize("hasRole('PROJECT_MANAGER')")
public class ProjectManagerOrderController {
    private final AdminOrderDetailService details;

    public ProjectManagerOrderController(AdminOrderDetailService details) { this.details = details; }

    @GetMapping("/{id}")
    ApiResponse<ProjectManagerOrderDetailView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                                      @PathVariable @Min(1) long id,
                                                      HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(details.projectManagerDetail(actor, id),
                requestId == null ? "" : requestId.toString());
    }
}
