package com.lczz.product.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.product.service.ProductService;
import com.lczz.product.service.ProductService.AuditContext;
import com.lczz.product.service.ProductService.CategoryCommand;
import com.lczz.product.service.ProductService.CategoryView;
import com.lczz.product.service.ProductService.ProductCommand;
import com.lczz.product.service.ProductService.ProductPage;
import com.lczz.product.service.ProductService.ProductView;
import com.lczz.product.service.ProductService.StockAdjustmentCommand;
import com.lczz.product.service.ProductSkuService.DimensionCommand;
import com.lczz.product.service.ProductSkuService.SkuCommand;
import com.lczz.product.service.ProductSkuService.ValueCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
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
@RequestMapping({"/api/consumables", "/api/v1/consumables"})
@Tag(name = "产品与耗材")
public class ProductController {
    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询产品；非管理员仅返回已启用产品")
    ApiResponse<ProductPage> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @RequestParam(defaultValue = "1") @Min(1) int page,
                                  @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                                  @RequestParam(required = false) @Size(max = 100) String keyword,
                                  @RequestParam(required = false) @Size(max = 128) String category,
                                  @RequestParam(required = false) Boolean enabled,
                                  @RequestParam(required = false) @Size(max = 16) String stockStatus,
                                  HttpServletRequest request) {
        return ApiResponse.success(productService.list(actor, page, pageSize, keyword, category, enabled, stockStatus),
                requestId(request));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "查询产品详情")
    ApiResponse<ProductView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(productService.detail(actor, id), requestId(request));
    }

    @GetMapping("/categories")
    @Operation(summary = "查询两级产品分类")
    ApiResponse<List<CategoryView>> categories(@AuthenticationPrincipal AuthenticatedUser actor,
                                               HttpServletRequest request) {
        return ApiResponse.success(productService.categories(actor), requestId(request));
    }

    @PostMapping("/categories")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员创建产品分类")
    ApiResponse<CategoryView> createCategory(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @Valid @RequestBody CategoryRequest body,
                                             HttpServletRequest request) {
        return ApiResponse.success(productService.createCategory(actor, body.toCommand()), requestId(request));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员更新产品分类")
    ApiResponse<CategoryView> updateCategory(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @PathVariable @Min(1) long id,
                                             @Valid @RequestBody CategoryRequest body,
                                             HttpServletRequest request) {
        return ApiResponse.success(productService.updateCategory(actor, id, body.toCommand()), requestId(request));
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员删除未使用分类")
    ApiResponse<Boolean> deleteCategory(@PathVariable @Min(1) long id, HttpServletRequest request) {
        productService.deleteCategory(id);
        return ApiResponse.success(true, requestId(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员创建产品")
    ApiResponse<ProductView> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @Valid @RequestBody ProductRequest body,
                                    HttpServletRequest request) {
        return ApiResponse.success(productService.create(actor, body.toCommand()), requestId(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员更新产品")
    ApiResponse<ProductView> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long id,
                                    @Valid @RequestBody ProductRequest body,
                                    HttpServletRequest request) {
        return ApiResponse.success(productService.update(actor, id, body.toCommand()), requestId(request));
    }

    @PatchMapping("/{id}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员上架或下架产品")
    ApiResponse<ProductView> setEnabled(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable @Min(1) long id,
                                        @Valid @RequestBody EnabledRequest body,
                                        HttpServletRequest request) {
        return ApiResponse.success(productService.setEnabled(actor, id, body.enabled()), requestId(request));
    }

    @PostMapping("/{id}/stock-adjustment")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员按原因执行耗材入库或出库")
    ApiResponse<ProductView> adjustStock(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable @Min(1) long id,
                                         @Valid @RequestBody StockAdjustmentRequest body,
                                         HttpServletRequest request) {
        AuditContext context = new AuditContext(requestId(request), request.getRemoteAddr());
        return ApiResponse.success(productService.adjustStock(actor, id, body.toCommand(), context),
                requestId(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员逻辑删除产品")
    ApiResponse<Boolean> delete(@PathVariable @Min(1) long id, HttpServletRequest request) {
        productService.delete(id);
        return ApiResponse.success(true, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record ProductRequest(
            @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String productCode,
            @NotBlank @Size(max = 255) String name,
            @NotNull @Min(1) Long categoryId,
            @Size(max = 255) String spec,
            @NotBlank @Size(max = 32) String unit,
            @DecimalMin("0") BigDecimal stock,
            @DecimalMin("0") BigDecimal price,
            @Size(max = 5000) String remark,
            @Min(1) Long coverFileId,
            @Size(max = 3) List<@Min(1) Long> imageFileIds,
            @Size(max = 9) List<@Min(1) Long> detailFileIds,
            Boolean enabled,
            Integer sortOrder,
            @Size(max = 8) List<@Valid SpecDimensionRequest> specDimensions,
            @Size(max = 500) List<@Valid SkuRequest> skus) {
        ProductCommand toCommand() {
            return new ProductCommand(productCode, name, categoryId, spec, unit, stock, price, remark,
                    coverFileId, imageFileIds, detailFileIds, enabled, sortOrder,
                    specDimensions == null ? List.of() : specDimensions.stream().map(SpecDimensionRequest::toCommand).toList(),
                    skus == null ? List.of() : skus.stream().map(SkuRequest::toCommand).toList());
        }
    }

    record SpecDimensionRequest(@NotBlank @Size(max = 64) String name,
                                @NotNull @Size(min = 1, max = 100) List<@Valid SpecValueRequest> values,
                                Integer sortOrder) {
        DimensionCommand toCommand() {
            return new DimensionCommand(name, values.stream().map(SpecValueRequest::toCommand).toList(), sortOrder);
        }
    }

    record SpecValueRequest(@NotBlank @Size(max = 128) String value, Integer sortOrder) {
        ValueCommand toCommand() { return new ValueCommand(value, sortOrder); }
    }

    record SkuRequest(@Min(1) Long id,
                      @Pattern(regexp = "^[A-Za-z0-9_-]{1,96}$") String code,
                      Map<@NotBlank @Size(max = 64) String, @NotBlank @Size(max = 128) String> specValues,
                      @NotBlank @Size(max = 32) String unit,
                      @NotNull @DecimalMin("0") BigDecimal stock,
                      Boolean enabled, Integer sortOrder) {
        SkuCommand toCommand() { return new SkuCommand(id, code, specValues, unit, stock, enabled, sortOrder); }
    }

    record CategoryRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,64}$") String code,
            @NotBlank @Size(max = 128) String name,
            @Min(1) Long parentId,
            Integer sortOrder,
            Boolean enabled) {
        CategoryCommand toCommand() {
            return new CategoryCommand(code, name, parentId, sortOrder, enabled);
        }
    }

    record EnabledRequest(@NotNull Boolean enabled) { }

    record StockAdjustmentRequest(
            @Min(1) Long skuId,
            @NotBlank @Pattern(regexp = "(?i)IN|OUT") String type,
            @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
            @NotBlank @Size(min = 2, max = 500) String reason) {
        StockAdjustmentCommand toCommand() {
            return new StockAdjustmentCommand(skuId, type, quantity, reason);
        }
    }
}
