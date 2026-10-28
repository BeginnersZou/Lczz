package com.lczz.order.web;

import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.SalespersonService;
import com.lczz.order.service.SalespersonService.OptionPage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/salespersons", "/api/v1/salespersons"})
@PreAuthorize("hasAnyRole('ADMIN', 'DEALER', 'SALES')")
public class SalespersonController {
    private final SalespersonService service;

    public SalespersonController(SalespersonService service) { this.service = service; }

    @GetMapping("/options")
    ApiResponse<OptionPage> options(@RequestParam(defaultValue = "1") @Min(1) int page,
                                    @RequestParam(defaultValue = "30") @Min(1) @Max(100) int pageSize,
                                    @RequestParam(required = false) @Size(max = 100) String keyword,
                                    HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(service.options(page, pageSize, keyword),
                requestId == null ? "" : requestId.toString());
    }
}
