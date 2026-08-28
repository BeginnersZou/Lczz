package com.lczz.file.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.file.service.FileService;
import com.lczz.file.service.FileService.FileView;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/upload", "/api/v1/upload"})
public class GenericImageUploadCompatibilityController {
    private final FileService fileService;

    public GenericImageUploadCompatibilityController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "兼容小程序评价页的通用图片上传入口")
    ApiResponse<FileView> upload(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @RequestPart("file") MultipartFile file,
                                 HttpServletRequest request) {
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(fileService.upload(actor, file, null),
                requestId == null ? "" : requestId.toString());
    }
}
