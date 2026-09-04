package com.lczz.product.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.product.service.InstallerSelfOrderService;
import com.lczz.product.service.InstallerSelfOrderService.CartView;
import com.lczz.product.service.InstallerSelfOrderService.SelfOrderPage;
import com.lczz.product.service.InstallerSelfOrderService.SelfOrderView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/installer", "/api/v1/installer"})
@PreAuthorize("hasRole('INSTALLER')")
public class InstallerSelfOrderController {
    private final InstallerSelfOrderService service;

    public InstallerSelfOrderController(InstallerSelfOrderService service) { this.service = service; }

    @GetMapping("/cart")
    ApiResponse<CartView> cart(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(service.cart(actor), requestId(request));
    }

    @PostMapping("/cart/items")
    ApiResponse<CartView> add(@AuthenticationPrincipal AuthenticatedUser actor,
                              @Valid @RequestBody AddRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.add(actor, body.skuId(), body.resolvedQuantity()), requestId(request));
    }

    @PatchMapping("/cart/items/{id}")
    ApiResponse<CartView> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable @Min(1) long id,
                                 @Valid @RequestBody QuantityRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(actor, id, body.quantity()), requestId(request));
    }

    @DeleteMapping("/cart/items/{id}")
    ApiResponse<CartView> remove(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable @Min(1) long id,
                                 HttpServletRequest request) {
        return ApiResponse.success(service.remove(actor, id), requestId(request));
    }

    @DeleteMapping("/cart")
    ApiResponse<CartView> clear(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(service.clear(actor), requestId(request));
    }

    @PostMapping("/self-orders")
    ApiResponse<SelfOrderView> submit(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @Valid @RequestBody SubmitRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.submit(actor, body.requestId()), requestId(request));
    }

    @GetMapping("/self-orders")
    ApiResponse<SelfOrderPage> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @RequestParam(defaultValue = "1") @Min(1) int page,
                                    @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                                    HttpServletRequest request) {
        return ApiResponse.success(service.list(actor, page, pageSize), requestId(request));
    }

    @GetMapping("/self-orders/{id}")
    ApiResponse<SelfOrderView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(actor, id), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record AddRequest(@NotNull @Min(1) Long skuId, @Min(1) Integer quantity) {
        int resolvedQuantity() { return quantity == null ? 1 : quantity; }
    }
    record QuantityRequest(@NotNull @Min(1) Integer quantity) { }
    record SubmitRequest(@NotBlank @Size(max = 64) String requestId) { }
}
