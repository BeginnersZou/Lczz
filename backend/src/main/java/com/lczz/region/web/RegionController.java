package com.lczz.region.web;

import com.lczz.common.api.ApiResponse;
import com.lczz.region.service.RegionService;
import com.lczz.region.service.RegionService.RegionNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/regions", "/api/v1/regions"})
@Tag(name = "行政区划")
public class RegionController {
    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @GetMapping("/tree")
    @Operation(summary = "查询省、市、区三级行政区划树")
    ApiResponse<List<RegionNode>> tree(HttpServletRequest request) {
        return ApiResponse.success(regionService.tree(), requestId(request));
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute("requestId");
        return value == null ? "" : value.toString();
    }
}
