package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.common.exception.BusinessException;
import com.lczz.order.service.DealerAppointmentService;
import com.lczz.order.service.DealerAppointmentService.Command;
import com.lczz.order.service.DealerAppointmentService.Receipt;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/dealer/appointments", "/api/v1/dealer/appointments",
        "/api/mini/dealer/appointments", "/api/v1/mini/dealer/appointments"})
@PreAuthorize("hasRole('DEALER')")
@Tag(name = "经销商预约安装")
public class DealerAppointmentController {
    private final DealerAppointmentService service;

    public DealerAppointmentController(DealerAppointmentService service) { this.service = service; }

    @PostMapping
    @Operation(summary = "经销商提交预约安装；同一 requestId 重试返回已有回执")
    ApiResponse<Receipt> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                @Valid @RequestBody AppointmentRequest body, HttpServletRequest request) {
        return ApiResponse.success(service.create(actor, body.toCommand()),
                String.valueOf(request.getAttribute("requestId")));
    }

    record AppointmentRequest(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String requestId,
            @NotBlank @Size(max = 64) String taskType,
            @NotBlank @Size(max = 1000) String description,
            @NotBlank @Size(max = 64) String customerName,
            @NotBlank @Pattern(regexp = "^(?:\\+86)?1[3-9]\\d{9}$") String customerPhone,
            @NotNull @Size(min = 3, max = 3) List<@NotBlank @Size(max = 64) String> addressArea,
            @NotBlank @Size(max = 500) String addressDetail,
            @Size(max = 9) List<@NotNull @Min(1) Long> fileIds) {
        @JsonAnySetter
        public void rejectUnknown(String name, Object value) {
            throw new BusinessException("INVALID_APPOINTMENT_FIELD", "预约包含不支持的字段");
        }
        Command toCommand() {
            return new Command(requestId, taskType, description, customerName, customerPhone,
                    addressArea, addressDetail, fileIds);
        }
    }
}
