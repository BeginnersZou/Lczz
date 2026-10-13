package com.lczz.product.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.product.service.RecipientAddressService;
import com.lczz.product.service.RecipientAddressService.AddressView;
import com.lczz.product.service.RecipientInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/installer/addresses", "/api/v1/installer/addresses"})
@PreAuthorize("hasRole('INSTALLER')")
public class RecipientAddressController {
    private final RecipientAddressService service;
    public RecipientAddressController(RecipientAddressService service) { this.service = service; }

    @GetMapping
    ApiResponse<List<AddressView>> list(@AuthenticationPrincipal AuthenticatedUser actor, HttpServletRequest request) {
        return ApiResponse.success(service.list(actor), requestId(request));
    }

    @PostMapping
    ApiResponse<AddressView> create(@AuthenticationPrincipal AuthenticatedUser actor, @Valid @RequestBody AddressRequest body,
                                    HttpServletRequest request) {
        return ApiResponse.success(service.create(actor, body.info(), Boolean.TRUE.equals(body.isDefault())), requestId(request));
    }

    @PutMapping("/{id}")
    ApiResponse<AddressView> update(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable @Min(1) long id,
                                    @Valid @RequestBody AddressRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.update(actor, id, body.info(), Boolean.TRUE.equals(body.isDefault())), requestId(request));
    }

    @PatchMapping("/{id}/default")
    ApiResponse<AddressView> setDefault(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable @Min(1) long id,
                                        HttpServletRequest request) {
        return ApiResponse.success(service.setDefault(actor, id), requestId(request));
    }

    @DeleteMapping("/{id}")
    ApiResponse<Boolean> delete(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable @Min(1) long id,
                                HttpServletRequest request) {
        service.delete(actor, id);
        return ApiResponse.success(true, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record AddressRequest(@NotBlank @Size(max = 64) String recipientName, @NotBlank @Size(max = 32) String recipientPhone,
                           @NotBlank @Size(max = 500) String recipientAddress, Boolean isDefault) {
        RecipientInfo info() { return new RecipientInfo(recipientName, recipientPhone, recipientAddress); }
    }
}
