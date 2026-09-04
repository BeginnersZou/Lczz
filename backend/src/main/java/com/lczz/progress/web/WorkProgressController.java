package com.lczz.progress.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.common.exception.BusinessException;
import com.lczz.progress.service.WorkProgressService;
import com.lczz.progress.service.WorkProgressService.ProgressCommand;
import com.lczz.progress.service.WorkProgressService.ProgressView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping({"/api/orders", "/api/v1/orders"})
@Tag(name = "施工进度")
public class WorkProgressController {
    private final WorkProgressService progressService;

    public WorkProgressController(WorkProgressService progressService) {
        this.progressService = progressService;
    }

    @GetMapping("/{orderId}/progress")
    @Operation(summary = "订单相关角色按提交时间查询施工进度和完工信息")
    ApiResponse<List<ProgressView>> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable @Min(1) long orderId, HttpServletRequest request) {
        return ApiResponse.success(progressService.list(actor, orderId), requestId(request));
    }

    @PostMapping("/{orderId}/progress")
    @Operation(summary = "指派安装师傅提交一条施工进度")
    ApiResponse<ProgressView> submit(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable @Min(1) long orderId,
                                     @Valid @RequestBody ProgressRequest body, HttpServletRequest request) {
        return ApiResponse.success(progressService.submitProgress(actor, orderId, body.toCommand()), requestId(request));
    }

    @PostMapping("/{orderId}/completion")
    @PreAuthorize("hasRole('INSTALLER')")
    @Operation(summary = "已停用师傅完工提交，请由绑定客户确认订单完成", deprecated = true)
    ApiResponse<Void> complete(@PathVariable @Min(1) long orderId) {
        throw new BusinessException(410, "COMPLETION_ENDPOINT_RETIRED", "师傅完工提交已停用，请由绑定客户确认订单完成");
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record ProgressRequest(@NotBlank @Size(max = 2000) String description,
                           @Size(max = 9) List<@NotNull @Min(1) Long> fileIds) {
        ProgressCommand toCommand() { return new ProgressCommand(description, fileIds); }
    }

}
