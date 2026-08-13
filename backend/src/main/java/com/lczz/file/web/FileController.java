package com.lczz.file.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.file.service.FileService;
import com.lczz.file.service.FileService.FileContent;
import com.lczz.file.service.FileService.FileView;
import com.lczz.file.service.FileService.RelationCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping({"/api/files", "/api/v1/files"})
@Tag(name = "统一文件服务")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传单张图片；可同时原子绑定业务关系")
    ApiResponse<FileView> upload(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) String businessType,
                                 @RequestParam(required = false) @Min(1) Long businessId,
                                 @RequestParam(required = false) String usageType,
                                 @RequestParam(required = false) Integer sortOrder,
                                 HttpServletRequest request) {
        RelationCommand relation = new RelationCommand(businessType, businessId, usageType, sortOrder);
        return ApiResponse.success(fileService.upload(actor, file, relation), requestId(request));
    }

    @PostMapping("/{id}/relations")
    @Operation(summary = "将已上传文件绑定到业务；限制只能绑定本人上传文件")
    ApiResponse<FileView> bind(@AuthenticationPrincipal AuthenticatedUser actor,
                               @PathVariable @Min(1) long id,
                               @Valid @RequestBody RelationRequest body,
                               HttpServletRequest request) {
        return ApiResponse.success(fileService.bind(actor, id, body.toCommand()), requestId(request));
    }

    @GetMapping("/{id}/url")
    @Operation(summary = "校验权限并签发短时文件访问地址")
    ApiResponse<FileView> accessUrl(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable @Min(1) long id, HttpServletRequest request) {
        return ApiResponse.success(fileService.issueAccess(actor, id), requestId(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "携带登录令牌直接读取文件")
    ResponseEntity<Resource> authenticatedContent(@AuthenticationPrincipal AuthenticatedUser actor,
                                                   @PathVariable @Min(1) long id) {
        return contentResponse(fileService.authenticatedContent(actor, id));
    }

    @GetMapping("/access/{id}")
    @Operation(summary = "使用短时签名读取文件，无需额外登录请求头")
    ResponseEntity<Resource> signedContent(@PathVariable @Min(1) long id,
                                            @RequestParam long expires,
                                            @RequestParam @NotBlank @Size(max = 100) String signature) {
        return contentResponse(fileService.signedContent(id, expires, signature));
    }

    private ResponseEntity<Resource> contentResponse(FileContent content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.metadata().getMimeType()));
        headers.setContentLength(content.metadata().getFileSize());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.metadata().getOriginalName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noCache().cachePrivate());
        return ResponseEntity.ok().headers(headers).body(content.resource());
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }

    record RelationRequest(@NotBlank @Size(max = 32) String businessType,
                           @Min(1) long businessId,
                           @NotBlank @Size(max = 32) String usageType,
                           Integer sortOrder) {
        RelationCommand toCommand() { return new RelationCommand(businessType, businessId, usageType, sortOrder); }
    }
}
