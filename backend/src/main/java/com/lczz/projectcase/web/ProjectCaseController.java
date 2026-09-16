package com.lczz.projectcase.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.projectcase.service.ProjectCaseService;
import com.lczz.projectcase.service.ProjectCaseService.ProjectCaseCommand;
import com.lczz.projectcase.service.ProjectCaseService.ProjectCaseDetailView;
import com.lczz.projectcase.service.ProjectCaseService.ProjectCasePage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/api/cases", "/api/v1/cases"})
@Tag(name = "项目案例")
public class ProjectCaseController {
    private final ProjectCaseService projectCaseService;

    public ProjectCaseController(ProjectCaseService projectCaseService) {
        this.projectCaseService = projectCaseService;
    }

    @GetMapping("/list")
    @Operation(summary = "公开分页查询项目案例")
    ApiResponse<ProjectCasePage> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @RequestParam(defaultValue = "1") @Min(1) int page,
                                      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int pageSize,
                                      @RequestParam(required = false) @Size(max = 255) String keyword,
                                      HttpServletRequest request) {
        return ApiResponse.success(projectCaseService.list(actor, page, pageSize, keyword), requestId(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "公开查询项目案例详情")
    ApiResponse<ProjectCaseDetailView> detail(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable @Min(1) long id,
                                              HttpServletRequest request) {
        return ApiResponse.success(projectCaseService.detail(actor, id), requestId(request));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员创建项目案例")
    ApiResponse<ProjectCaseDetailView> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @Valid @RequestBody ProjectCaseRequest body,
                                              HttpServletRequest request) {
        return ApiResponse.success(projectCaseService.create(actor, body.toCommand()), requestId(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员更新项目案例")
    ApiResponse<ProjectCaseDetailView> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                              @PathVariable @Min(1) long id,
                                              @Valid @RequestBody ProjectCaseRequest body,
                                              HttpServletRequest request) {
        return ApiResponse.success(projectCaseService.update(actor, id, body.toCommand()), requestId(request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "管理员删除项目案例及未被引用的图片")
    ApiResponse<Boolean> delete(@PathVariable @Min(1) long id, HttpServletRequest request) {
        projectCaseService.delete(id);
        return ApiResponse.success(true, requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record ProjectCaseRequest(@NotBlank @Size(max = 255) String siteName,
                              @NotNull @Size(min = 1) List<@NotNull @Min(1) Long> imageFileIds) {
        ProjectCaseCommand toCommand() { return new ProjectCaseCommand(siteName, imageFileIds); }
    }
}
