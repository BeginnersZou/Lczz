package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.AdminOrderDetailService;
import com.lczz.order.service.AdminOrderDetailService.AdminOrderDetailView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping({"/api/admin/orders", "/api/v1/admin/orders"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台订单详情")
public class AdminOrderDetailController {
    private final AdminOrderDetailService detailService;

    public AdminOrderDetailController(AdminOrderDetailService detailService) {
        this.detailService = detailService;
    }

    @GetMapping("/{id}")
    @Operation(summary = "管理员查看订单、施工进度、全部耗材申请及评价",
            description = "未提交进度或耗材时返回空数组，未评价时 review 为 null；不存在或已删除订单返回 404。")
    ApiResponse<AdminOrderDetailView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                            @PathVariable @Min(1) long id, HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(detailService.detail(actor, id), requestId == null ? "" : requestId.toString());
    }
}
