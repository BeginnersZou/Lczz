package com.lczz.stocking.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.stocking.service.MaterialRequestService;
import com.lczz.stocking.service.MaterialRequestService.ItemCommand;
import com.lczz.stocking.service.MaterialRequestService.PreparedItemCommand;
import com.lczz.stocking.service.MaterialRequestService.RequestPage;
import com.lczz.stocking.service.MaterialRequestService.RequestView;
import com.lczz.stocking.service.MaterialRequestService.SubmitCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api", "/api/v1"})
@Tag(name = "耗材申请与订单备货")
public class MaterialRequestController {
    private final MaterialRequestService service;

    public MaterialRequestController(MaterialRequestService service) {
        this.service = service;
    }

    @PostMapping("/orders/{orderId}/materials")
    @PreAuthorize("hasRole('INSTALLER')")
    @Operation(summary = "安装师傅为指派给自己的订单提交耗材申请")
    ApiResponse<RequestView> submit(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long orderId,
                                    @Valid @RequestBody SubmitRequest body,
                                    HttpServletRequest request) {
        return ApiResponse.success(service.submit(actor, orderId, body.toCommand()), requestId(request));
    }

    @GetMapping("/orders/{orderId}/materials")
    @Operation(summary = "按订单数据权限查询最近一次耗材申请")
    ApiResponse<RequestView> byOrder(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable @Min(1) long orderId,
                                     HttpServletRequest request) {
        return ApiResponse.success(service.byOrder(actor, orderId), requestId(request));
    }

    @GetMapping("/preparation/list")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员分页查询订单备货申请")
    ApiResponse<RequestPage> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                                  @RequestParam(required = false) @Size(max = 100) String keyword,
                                  @RequestParam(required = false) @Size(max = 32) String status,
                                  HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, keyword, status), requestId(request));
    }

    @GetMapping("/preparation/detail/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员查询备货详情与产品快照")
    ApiResponse<RequestView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(actor, id), requestId(request));
    }

    @PostMapping("/preparation/{id}/prepare")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员保存各耗材的备货进度")
    ApiResponse<RequestView> prepare(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable @Min(1) long id,
                                     @Valid @RequestBody PrepareRequest body,
                                     HttpServletRequest request) {
        return ApiResponse.success(service.prepare(actor, id, body.toCommands()), requestId(request));
    }

    @PostMapping("/preparation/{id}/finish")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员确认全部耗材已备货")
    ApiResponse<RequestView> finish(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(service.finish(actor, id), requestId(request));
    }

    @PostMapping("/preparation/{id}/void")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员作废未完成的耗材申请")
    ApiResponse<RequestView> voidRequest(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable @Min(1) long id,
                                         @Valid @RequestBody VoidRequest body,
                                         HttpServletRequest request) {
        return ApiResponse.success(service.voidRequest(actor, id, body.reason()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record SubmitRequest(@NotEmpty @Size(max = 100) List<@Valid ItemRequest> items,
                         @Size(max = 500) String remark) {
        SubmitCommand toCommand() {
            return new SubmitCommand(items.stream().map(ItemRequest::toCommand).toList(), remark);
        }
    }
    record ItemRequest(@NotNull @Min(1) Long productId,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity) {
        ItemCommand toCommand() { return new ItemCommand(productId, quantity); }
    }
    record PrepareRequest(@NotEmpty @Size(max = 100) List<@Valid PreparedItemRequest> materials) {
        List<PreparedItemCommand> toCommands() {
            return materials.stream().map(PreparedItemRequest::toCommand).toList();
        }
    }
    record PreparedItemRequest(@NotNull @Min(1) Long id, @NotNull Boolean checked) {
        PreparedItemCommand toCommand() { return new PreparedItemCommand(id, checked); }
    }
    record VoidRequest(@Size(max = 500) String reason) { }
}
