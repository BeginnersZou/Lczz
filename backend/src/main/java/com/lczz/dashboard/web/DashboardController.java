package com.lczz.dashboard.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.dashboard.service.DashboardService;
import com.lczz.dashboard.service.DashboardService.Overview;
import com.lczz.dashboard.service.DashboardService.StatusMetric;
import com.lczz.dashboard.service.DashboardService.TodoPage;
import com.lczz.dashboard.service.DashboardService.Trend;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/dashboard", "/api/v1/dashboard"})
@Tag(name = "工作台")
public class DashboardController {
    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取管理端工作台核心指标")
    ApiResponse<Overview> overview(@RequestParam(defaultValue = "7d") @Size(max = 16) String range,
                                   HttpServletRequest request) {
        return ApiResponse.success(dashboardService.overview(range), requestId(request));
    }

    @GetMapping("/order-trend")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取订单创建趋势")
    ApiResponse<Trend> orderTrend(@RequestParam(defaultValue = "7d") @Size(max = 16) String range,
                                  HttpServletRequest request) {
        return ApiResponse.success(dashboardService.orderTrend(range), requestId(request));
    }

    @GetMapping("/order-status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "获取订单状态分布")
    ApiResponse<List<StatusMetric>> orderStatus(HttpServletRequest request) {
        return ApiResponse.success(dashboardService.orderStatus(), requestId(request));
    }

    @GetMapping("/todo")
    @Operation(summary = "按当前角色获取待办订单")
    ApiResponse<TodoPage> todo(@AuthenticationPrincipal AuthenticatedUser actor,
                               @RequestParam(defaultValue = "1") @Min(1) int page,
                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                               HttpServletRequest request) {
        return ApiResponse.success(dashboardService.todo(actor, page, pageSize), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }
}
