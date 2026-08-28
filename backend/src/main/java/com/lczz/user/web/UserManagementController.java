package com.lczz.user.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.user.service.UserManagementService;
import com.lczz.user.service.UserManagementService.AuditContext;
import com.lczz.user.service.UserManagementService.CreateCommand;
import com.lczz.user.service.UserManagementService.PasswordChangeCommand;
import com.lczz.user.service.UserManagementService.UpdateCommand;
import com.lczz.user.service.UserManagementService.UserPage;
import com.lczz.user.service.UserManagementService.UserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping({"/api/users", "/api/v1/users"})
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "后台用户管理")
public class UserManagementController {
    private final UserManagementService service;

    public UserManagementController(UserManagementService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @Operation(summary = "分页查询小程序与后台用户")
    ApiResponse<UserPage> list(@RequestParam(defaultValue = "1") @Min(1) int page,
                               @RequestParam(defaultValue = "10") @Min(1) @Max(100) int pageSize,
                               @RequestParam(required = false) @Size(max = 100) String keyword,
                               @RequestParam(required = false) @Size(max = 32) String role,
                               @RequestParam(required = false) @Size(max = 32) String accountStatus,
                               @RequestParam(required = false) Boolean blacklist,
                               HttpServletRequest request) {
        return ApiResponse.success(service.list(page, pageSize, keyword, role, accountStatus, blacklist),
                requestId(request));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "查询用户详情")
    ApiResponse<UserView> detail(@PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(service.detail(id), requestId(request));
    }

    @PostMapping
    @Operation(summary = "管理员按手机号预创建小程序用户")
    ApiResponse<UserView> create(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @Valid @RequestBody CreateRequest body,
                                 HttpServletRequest request) {
        return ApiResponse.success(service.create(actor, body.toCommand(), context(request)), requestId(request));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户安全资料与一期业务角色")
    ApiResponse<UserView> update(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable @Min(1) long id,
                                 @Valid @RequestBody UpdateRequest body,
                                 HttpServletRequest request) {
        return ApiResponse.success(service.update(actor, id, body.toCommand(), body.toPasswordCommand(),
                context(request)), requestId(request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "启用或停用用户账号")
    ApiResponse<UserView> changeStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable @Min(1) long id,
                                       @Valid @RequestBody StatusRequest body,
                                       HttpServletRequest request) {
        return ApiResponse.success(service.changeStatus(actor, id, body.accountStatus(), context(request)),
                requestId(request));
    }

    @PostMapping("/{id}/blacklist")
    @Operation(summary = "加入或移出用户黑名单")
    ApiResponse<UserView> changeBlacklist(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable @Min(1) long id,
                                          @Valid @RequestBody BlacklistRequest body,
                                          HttpServletRequest request) {
        return ApiResponse.success(service.changeBlacklist(actor, id, body.blacklist(), body.reason(),
                context(request)), requestId(request));
    }

    private AuditContext context(HttpServletRequest request) {
        return new AuditContext(requestId(request), request.getRemoteAddr());
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record UpdateRequest(@NotBlank @Size(max = 64) String nickname,
                         @Size(max = 64) String realName,
                         @Size(max = 16) String gender,
                         @NotBlank @Size(max = 32) String role,
                         @Size(max = 72) String originalPassword,
                         @Size(max = 72) String newPassword,
                         @Size(max = 72) String confirmPassword) {
        UpdateCommand toCommand() {
            return new UpdateCommand(nickname, realName, gender, role);
        }

        PasswordChangeCommand toPasswordCommand() {
            if (isEmpty(originalPassword) && isEmpty(newPassword) && isEmpty(confirmPassword)) return null;
            return new PasswordChangeCommand(originalPassword, newPassword, confirmPassword);
        }

        private boolean isEmpty(String value) {
            return value == null || value.isEmpty();
        }
    }

    record CreateRequest(@NotBlank @Size(max = 64) String nickname,
                         @Size(max = 64) String realName,
                         @Size(max = 16) String gender,
                         @NotBlank @Size(max = 20) String phone,
                         @NotBlank @Size(max = 32) String role) {
        CreateCommand toCommand() {
            return new CreateCommand(nickname, realName, gender, phone, role);
        }
    }

    record StatusRequest(@NotBlank @Size(max = 32) String accountStatus) { }

    record BlacklistRequest(@NotNull Boolean blacklist,
                            @NotBlank @Size(min = 2, max = 500) String reason) { }
}
