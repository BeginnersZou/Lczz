package com.lczz.file.web;

import com.lczz.file.service.ProductImageVariantService;
import com.lczz.file.service.ProductImageVariantService.PublicImageContent;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/public/product-images", "/api/v1/public/product-images"})
public class PublicProductImageController {
    private static final String IMMUTABLE_CACHE = "public, max-age=31536000, immutable";
    private final ProductImageVariantService imageService;

    public PublicProductImageController(ProductImageVariantService imageService) {
        this.imageService = imageService;
    }

    @GetMapping("/{fileId}/{variant}/{version}")
    ResponseEntity<?> image(@PathVariable long fileId, @PathVariable String variant,
                            @PathVariable String version,
                            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        PublicImageContent content = imageService.content(fileId, variant, version);
        HttpHeaders headers = headers(content);
        if (matches(ifNoneMatch, content.etag())) {
            return ResponseEntity.status(304).headers(headers).build();
        }
        headers.setContentLength(content.size());
        return ResponseEntity.ok().headers(headers).body(content.resource());
    }

    private HttpHeaders headers(PublicImageContent content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.mimeType()));
        headers.setETag(content.etag());
        headers.setCacheControl(IMMUTABLE_CACHE);
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8).build());
        return headers;
    }

    private boolean matches(String supplied, String etag) {
        if (supplied == null || supplied.isBlank()) return false;
        return Arrays.stream(supplied.split(",")).map(String::trim)
                .anyMatch(value -> "*".equals(value) || etag.equals(value)
                        || ("W/" + etag).equals(value));
    }
}
