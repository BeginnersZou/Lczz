package com.lczz.file.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.file.service.FileService;
import com.lczz.file.service.FileService.FileContent;
import com.lczz.file.service.FileService.FileView;
import com.lczz.file.service.FileService.RelationCommand;
import com.lczz.file.service.FileImageVariantService.ImageContent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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
    @Operation(summary = "上传单张图片或视频；可同时原子绑定业务关系")
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

    @DeleteMapping("/{id}/relations")
    @Operation(summary = "解绑文件与业务关系；校验当前用户的业务写权限")
    ApiResponse<Boolean> unbind(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable @Min(1) long id,
                                @RequestParam @NotBlank @Size(max = 32) String businessType,
                                @RequestParam @Min(1) long businessId,
                                @RequestParam @NotBlank @Size(max = 32) String usageType,
                                HttpServletRequest request) {
        RelationCommand relation = new RelationCommand(businessType, businessId, usageType, null);
        return ApiResponse.success(fileService.unbind(actor, id, relation), requestId(request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除本人尚未绑定业务的临时上传文件")
    ApiResponse<Boolean> deleteUnbound(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @PathVariable @Min(1) long id,
                                       HttpServletRequest request) {
        return ApiResponse.success(fileService.deleteUnboundOwned(actor, id), requestId(request));
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
    ResponseEntity<?> signedContent(@PathVariable @Min(1) long id,
                                    @RequestParam long expires,
                                    @RequestParam @NotBlank @Size(max = 100) String signature,
                                    @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                    String ifNoneMatch) {
        return signedContentResponse(fileService.signedContent(id, expires, signature), expires, ifNoneMatch);
    }

    @GetMapping("/access/{id}/{variant}")
    @Operation(summary = "使用短时签名读取文件缩略图或展示图")
    ResponseEntity<?> signedImageContent(@PathVariable @Min(1) long id,
                                         @PathVariable String variant,
                                         @RequestParam long expires,
                                         @RequestParam @NotBlank @Size(max = 100) String signature,
                                         @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                         String ifNoneMatch) {
        ImageContent content = fileService.signedImageContent(id, variant, expires, signature);
        HttpHeaders headers = cacheableHeaders(content.mimeType(), content.filename(), content.etag(), expires);
        if (matches(ifNoneMatch, content.etag())) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        headers.setContentLength(content.size());
        return ResponseEntity.ok().headers(headers).body(content.resource());
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

    private ResponseEntity<?> signedContentResponse(FileContent content, long expires, String ifNoneMatch) {
        String hash = content.metadata().getSha256();
        String etag = hash == null || hash.isBlank() ? null : "\"" + hash + "\"";
        HttpHeaders headers = cacheableHeaders(content.metadata().getMimeType(),
                content.metadata().getOriginalName(), etag, expires);
        if (etag != null && matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        headers.setContentLength(content.metadata().getFileSize());
        return ResponseEntity.ok().headers(headers).body(content.resource());
    }

    private HttpHeaders cacheableHeaders(String mimeType, String filename, String etag, long expires) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(filename, StandardCharsets.UTF_8).build());
        if (etag != null) headers.setETag(etag);
        long maxAge = Math.max(0, expires - Instant.now().getEpochSecond());
        headers.setCacheControl(CacheControl.maxAge(maxAge, TimeUnit.SECONDS).cachePrivate());
        return headers;
    }

    private boolean matches(String supplied, String etag) {
        if (supplied == null || supplied.isBlank() || etag == null) return false;
        return Arrays.stream(supplied.split(",")).map(String::trim)
                .anyMatch(value -> "*".equals(value) || etag.equals(value) || ("W/" + etag).equals(value));
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
