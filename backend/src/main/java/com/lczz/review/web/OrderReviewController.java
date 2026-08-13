package com.lczz.review.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.review.service.OrderReviewService;
import com.lczz.review.service.OrderReviewService.ReviewCommand;
import com.lczz.review.service.OrderReviewService.ReviewView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/orders/evaluation", "/api/v1/orders/evaluation"})
@Tag(name = "订单评价")
public class OrderReviewController {
    private final OrderReviewService reviewService;

    public OrderReviewController(OrderReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "订单相关角色查询评价；未评价时 data 为 null")
    ApiResponse<ReviewView> byOrder(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long orderId, HttpServletRequest request) {
        return ApiResponse.success(reviewService.byOrder(actor, orderId), requestId(request));
    }

    @GetMapping("/ids")
    @Operation(summary = "查询当前角色范围内已评价订单 ID")
    ApiResponse<List<String>> reviewedIds(@AuthenticationPrincipal AuthenticatedUser actor,
                                          HttpServletRequest request) {
        return ApiResponse.success(reviewService.reviewedOrderIds(actor), requestId(request));
    }

    @PostMapping
    @Operation(summary = "绑定客户对待评价订单提交一次评价")
    ApiResponse<ReviewView> submit(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @Valid @RequestBody ReviewRequest body, HttpServletRequest request) {
        return ApiResponse.success(reviewService.submit(actor, body.toCommand()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record ReviewRequest(@NotNull @Min(1) Long orderId,
                         @NotNull @Min(1) @Max(5) Integer score,
                         @NotBlank @Size(max = 2000) String content,
                         Boolean liked,
                         @Size(max = 20) String label,
                         @Size(max = 5) List<@NotBlank @Size(max = 20) String> labels,
                         @Size(max = 9) List<@NotNull @Min(1) Long> fileIds,
                         @Size(max = 9) List<@NotBlank @Size(max = 1000) String> images) {
        ReviewCommand toCommand() {
            return new ReviewCommand(orderId, score, content, liked, label, labels, fileIds, images);
        }
    }
}
