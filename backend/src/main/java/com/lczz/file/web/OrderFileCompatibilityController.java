package com.lczz.file.web;

import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.api.ApiResponse;
import com.lczz.file.service.FileService;
import com.lczz.file.service.FileService.FileView;
import com.lczz.file.service.FileService.RelationCommand;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Min;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping({"/api/orders", "/api/v1/orders"})
public class OrderFileCompatibilityController {
    private final FileService fileService;

    public OrderFileCompatibilityController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "兼容现有订单页面的图片上传入口")
    ApiResponse<FileView> upload(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @RequestPart("file") MultipartFile file,
                                 @RequestParam(required = false) @Min(1) Long orderId,
                                 HttpServletRequest request) {
        RelationCommand relation = orderId == null ? null
                : new RelationCommand("ORDER", orderId, "ATTACHMENT", null);
        Object requestId = request.getAttribute("requestId");
        return ApiResponse.success(fileService.upload(actor, file, relation), requestId == null ? "" : requestId.toString());
    }
}
