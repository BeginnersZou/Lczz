package com.lczz.order.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.order.service.AdminOrderEditorService;
import com.lczz.stocking.service.MaterialRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/admin/orders", "/api/v1/admin/orders"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台订单统一编辑")
public class AdminOrderEditorController {
    private final AdminOrderEditorService editor;
    public AdminOrderEditorController(AdminOrderEditorService editor) { this.editor = editor; }

    @GetMapping("/{id}/editor")
    @Operation(summary = "读取订单编辑回显及版本摘要", description = "仅返回当前有效耗材申请；已作废订单 editable=false。")
    ApiResponse<AdminOrderEditorService.EditorView> load(@AuthenticationPrincipal AuthenticatedUser actor,
                                                         @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(editor.load(actor, id), requestId(request));
    }

    @PutMapping("/{id}/editor")
    @Operation(summary = "事务保存订单基础信息、施工记录与耗材清单",
            description = "未修改板块可省略；requestId 供相同请求重试，revision 防止覆盖并发修改。管理员可更正已完成订单，作废订单只读。")
    ApiResponse<AdminOrderEditorService.EditorView> save(@AuthenticationPrincipal AuthenticatedUser actor,
                                                         @PathVariable @Min(1) long id, @Valid @RequestBody EditRequest body,
                                                         HttpServletRequest request) {
        var command = new AdminOrderEditorService.EditCommand(body.requestId(), body.revision(),
                body.order() == null ? null : body.order().toCommand(), body.attachmentFileIds(),
                body.progress() == null ? null : body.progress().stream().map(p ->
                        new AdminOrderEditorService.ProgressEdit(p.id(), p.description(), p.fileIds())).toList(),
                body.materials() == null ? null : new MaterialRequestService.SubmitCommand(body.materials().items().stream()
                        .map(i -> new MaterialRequestService.ItemCommand(i.productId(), i.skuId(), i.quantity())).toList(),
                        body.materials().remark()));
        return ApiResponse.success(editor.save(actor, id, command, request.getRemoteAddr()), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record EditRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String requestId,
                       @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String revision,
                       @Valid OrderController.OrderRequest order,
                       @Size(max = 9) List<@NotNull @Min(1) Long> attachmentFileIds,
                       @Size(max = 1000) List<@NotNull @Valid ProgressRequest> progress,
                       @Valid MaterialRequest materials) { }
    record ProgressRequest(@NotNull @Min(1) Long id, @NotBlank @Size(max = 2000) String description,
                           @Size(max = 9) List<@NotNull @Min(1) Long> fileIds) { }
    record MaterialRequest(@NotNull @Size(max = 100) List<@NotNull @Valid MaterialItem> items,
                           @Size(max = 500) String remark) { }
    record MaterialItem(@NotNull @Min(1) Long productId, @Min(1) Long skuId,
                        @NotNull @DecimalMin("1") @DecimalMax("999999999") @Digits(integer = 9, fraction = 0) BigDecimal quantity) { }
}
