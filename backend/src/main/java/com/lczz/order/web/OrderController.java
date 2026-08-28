package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.OrderService;
import com.lczz.order.service.OrderService.InstallerView;
import com.lczz.order.service.OrderService.OrderCommand;
import com.lczz.order.service.OrderService.OrderPage;
import com.lczz.order.service.OrderService.OrderView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
@Tag(name = "安装订单")
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/list")
    @Operation(summary = "按当前角色的数据范围分页查询订单")
    ApiResponse<OrderPage> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                @RequestParam(defaultValue = "1") @Min(1) int page,
                                @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                                @RequestParam(required = false) @Size(max = 100) String keyword,
                                @RequestParam(required = false) @Size(max = 32) String status,
                                @RequestParam(required = false) @Size(max = 40) String startDate,
                                @RequestParam(required = false) @Size(max = 40) String endDate,
                                HttpServletRequest request) {
        return ApiResponse.success(orderService.list(actor, page, pageSize, keyword, status, startDate, endDate),
                requestId(request));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "在当前角色数据范围内查询订单详情")
    ApiResponse<OrderView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(orderService.detail(actor, id), requestId(request));
    }

    @GetMapping("/masters")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "查询可指派的安装师傅")
    ApiResponse<List<InstallerView>> masters(@RequestParam(required = false) @Size(max = 100) String keyword,
                                              HttpServletRequest request) {
        return ApiResponse.success(orderService.installers(keyword), requestId(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员创建订单并指派唯一安装师傅")
    ApiResponse<OrderView> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @Valid @RequestBody OrderRequest body, HttpServletRequest request) {
        return ApiResponse.success(orderService.create(actor, body.toCommand()), requestId(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员编辑订单；已结束订单不可编辑")
    ApiResponse<OrderView> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable @Min(1) long id,
                                  @Valid @RequestBody OrderRequest body, HttpServletRequest request) {
        return ApiResponse.success(orderService.update(actor, id, body.toCommand()), requestId(request));
    }

    @PostMapping("/{id}/assign-master")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员重新指派唯一安装师傅")
    ApiResponse<OrderView> assign(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable @Min(1) long id,
                                  @Valid @RequestBody AssignRequest body, HttpServletRequest request) {
        return ApiResponse.success(orderService.assign(actor, id, body.masterIds(), body.reason()), requestId(request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员按合法状态机修改订单状态")
    ApiResponse<OrderView> changeStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable @Min(1) long id,
                                        @Valid @RequestBody StatusRequest body, HttpServletRequest request) {
        return ApiResponse.success(orderService.changeStatus(actor, id, body.status(), body.reason()), requestId(request));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员作废订单")
    ApiResponse<OrderView> cancel(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable @Min(1) long id,
                                  @RequestBody(required = false) CancelRequest body,
                                  HttpServletRequest request) {
        return ApiResponse.success(orderService.cancel(actor, id, body == null ? null : body.reason()), requestId(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员删除操作按作废订单处理，保留业务历史")
    ApiResponse<OrderView> delete(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(orderService.cancel(actor, id, "管理员删除/作废"), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record OrderRequest(
            @NotBlank @Size(max = 64) String taskType,
            @Size(max = 1000) String description,
            @NotBlank @Size(max = 64) String customerName,
            @NotBlank @Pattern(regexp = "^(?:\\+86)?1[3-9]\\d{9}$") String customerPhone,
            @NotNull @Size(min = 3, max = 3) List<@NotBlank @Size(max = 64) String> addressArea,
            @NotBlank @Size(max = 500) String addressDetail,
            @NotBlank @Size(max = 40) String orderStartTime,
            @NotBlank @Size(max = 40) String orderEndTime,
            @NotNull @Size(min = 1, max = 1) List<@NotNull @Min(1) Long> masterIds,
            @Size(max = 1000) String adminRemark) {
        OrderCommand toCommand() {
            return new OrderCommand(taskType, description, customerName, customerPhone, addressArea,
                    addressDetail, orderStartTime, orderEndTime, masterIds, adminRemark);
        }
    }

    record AssignRequest(@NotNull @Size(min = 1, max = 1) List<@NotNull @Min(1) Long> masterIds,
                         @Size(max = 500) String reason) { }
    record StatusRequest(@NotBlank @Size(max = 32) String status, @Size(max = 500) String reason) { }
    record CancelRequest(@Size(max = 500) String reason) { }
}
