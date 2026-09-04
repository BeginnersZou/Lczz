package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.OrderConfirmationService;
import com.lczz.order.service.OrderConfirmationService.ConfirmationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
@Tag(name = "客户确认订单完成")
public class OrderConfirmationController {
    private final OrderConfirmationService confirmationService;

    public OrderConfirmationController(OrderConfirmationService confirmationService) {
        this.confirmationService = confirmationService;
    }

    @PostMapping("/{id}/confirm-completion")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DEALER')")
    @Operation(summary = "绑定客户确认处理中订单完成并封存施工进度",
            description = "仅绑定客户可操作；成功后进入 PENDING_REVIEW，重复确认返回 409，非本人订单返回 404。")
    ApiResponse<ConfirmationView> confirm(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable @Min(1) long id, HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(confirmationService.confirm(actor, id), requestId == null ? "" : requestId.toString());
    }
}
