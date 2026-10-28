package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.DealerAppointmentService;
import com.lczz.order.service.DealerAppointmentService.Receipt;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/sales/appointments", "/api/v1/sales/appointments"})
@PreAuthorize("hasRole('SALES')")
public class SalesAppointmentController {
    private final DealerAppointmentService service;

    public SalesAppointmentController(DealerAppointmentService service) { this.service = service; }

    @PostMapping
    ApiResponse<Receipt> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                @Valid @RequestBody DealerAppointmentController.AppointmentRequest body,
                                HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(service.create(actor, body.toCommand()),
                requestId == null ? "" : requestId.toString());
    }
}
