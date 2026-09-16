package com.lczz.file.service;

import com.lczz.common.exception.BusinessException;
import com.lczz.file.persistence.FileAssetRecord;
import com.lczz.file.persistence.FileAssetRecordMapper;
import com.lczz.file.storage.FileStorage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductImageVariantService {
    private static final Logger log = LoggerFactory.getLogger(ProductImageVariantService.class);
    private static final String URL_PREFIX = "/api/v1/public/product-images";
    private static final String JPEG_MIME = "image/jpeg";

    private final FileAssetRecordMapper fileMapper;
    private final FileStorage storage;
    private final JdbcTemplate jdbcTemplate;

    public ProductImageVariantService(FileAssetRecordMapper fileMapper, FileStorage storage,
                                      JdbcTemplate jdbcTemplate) {
        this.fileMapper = fileMapper;
        this.storage = storage;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ProductImageUrls urls(long fileId, String sha256) {
        String version = version(fileId, ensureSha256(fileId, sha256));
        return new ProductImageUrls(
                url(fileId, Variant.CARD, version),
                url(fileId, Variant.DISPLAY, version),
                url(fileId, Variant.ORIGINAL, version));
    }

    public PublicImageContent content(long fileId, String rawVariant, String requestedVersion) {
        Variant variant = Variant.parse(rawVariant);
        FileAssetRecord file = fileMapper.selectById(fileId);
        if (file == null || Boolean.TRUE.equals(file.getDeleted()) || file.getObjectKey() == null
                || file.getMimeType() == null || !file.getMimeType().startsWith("image/")) {
            throw notFound();
        }
        if (!isBoundToEnabledProduct(fileId) || !version(fileId, file.getSha256()).equals(requestedVersion)) {
            throw notFound();
        }
        Resource original = storage.load(file.getObjectKey());
        if (variant == Variant.ORIGINAL) return original(file, original);

        String derivedKey = derivedKey(file, variant);
        Resource cached = loadIfPresent(derivedKey);
        if (cached != null) return derived(file, variant, cached);
        try {
            byte[] bytes = transform(original, variant);
            if (bytes == null) return original(file, original);
            try {
                storage.store(derivedKey, new ByteArrayInputStream(bytes));
            } catch (IOException concurrentOrStorageFailure) {
                Resource concurrent = loadIfPresent(derivedKey);
                if (concurrent != null) return derived(file, variant, concurrent);
                throw concurrentOrStorageFailure;
            }
            return derived(file, variant, storage.load(derivedKey));
        } catch (Exception exception) {
            log.warn("Product image derivative fallback, fileId={}, variant={}, cause={}",
                    fileId, variant, exception.getClass().getSimpleName());
            return original(file, original);
        }
    }

    private boolean isBoundToEnabledProduct(long fileId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM product p
                WHERE p.deleted=0 AND p.enabled=1 AND (
                  p.cover_file_id=? OR EXISTS (
                    SELECT 1 FROM business_file_relation r
                    WHERE r.business_type='PRODUCT' AND r.business_id=p.id AND r.file_id=?
                      AND r.usage_type IN ('COVER', 'DETAIL', 'CAROUSEL')
                  )
                )
                """, Integer.class, fileId, fileId);
        return count != null && count > 0;
    }

    private byte[] transform(Resource original, Variant variant) throws IOException {
        long originalSize = original.contentLength();
        try (InputStream input = original.getInputStream()) {
            BufferedImage source = ImageIO.read(input);
            if (source == null) throw new IOException("Image decoder unavailable");
            int maxDimension = Math.max(source.getWidth(), source.getHeight());
            if (maxDimension <= variant.maxEdge && originalSize <= variant.targetBytes) return null;

            double scale = Math.min(1D, (double) variant.maxEdge / maxDimension);
            int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
            int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
            BufferedImage resized = resize(source, width, height);
            float[] qualities = {0.85F, 0.72F, 0.6F, 0.48F, 0.36F};
            byte[] best = null;
            for (int shrink = 0; shrink < 5; shrink++) {
                for (float quality : qualities) {
                    byte[] candidate = jpeg(resized, quality);
                    if (best == null || candidate.length < best.length) best = candidate;
                    if (candidate.length <= variant.targetBytes) return candidate;
                }
                int nextWidth = Math.max(1, (int) Math.round(resized.getWidth() * 0.82D));
                int nextHeight = Math.max(1, (int) Math.round(resized.getHeight() * 0.82D));
                if (nextWidth == resized.getWidth() && nextHeight == resized.getHeight()) break;
                resized = resize(resized, nextWidth, nextHeight);
            }
            return best;
        }
    }

    private BufferedImage resize(BufferedImage source, int width, int height) {
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return target;
    }

    private byte[] jpeg(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) throw new IOException("JPEG writer unavailable");
        ImageWriter writer = writers.next();
        try (ByteArrayOutputStream output = new ByteArrayOutputStream();
             ImageOutputStream imageOutput = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(imageOutput);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(quality);
            writer.write(null, new IIOImage(image, null, null), params);
            imageOutput.flush();
            return output.toByteArray();
        } finally {
            writer.dispose();
        }
    }

    private Resource loadIfPresent(String objectKey) {
        try {
            return storage.load(objectKey);
        } catch (BusinessException exception) {
            if (exception.getStatus() == 404) return null;
            throw exception;
        }
    }

    private PublicImageContent original(FileAssetRecord file, Resource resource) {
        return new PublicImageContent(resource, file.getMimeType(), length(resource), quote(fileEtag(file)),
                file.getOriginalName());
    }

    private PublicImageContent derived(FileAssetRecord file, Variant variant, Resource resource) {
        return new PublicImageContent(resource, JPEG_MIME, length(resource),
                quote(fileEtag(file) + "-" + variant.path + "-v1"), derivedName(file, variant));
    }

    private long length(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException exception) {
            throw new BusinessException(500, "PRODUCT_IMAGE_READ_FAILED", "产品图片读取失败");
        }
    }

    private String fileEtag(FileAssetRecord file) {
        return file.getSha256() == null || file.getSha256().isBlank()
                ? "legacy-" + file.getId() : file.getSha256().toLowerCase(Locale.ROOT);
    }

    private String derivedKey(FileAssetRecord file, Variant variant) {
        return "variants/product/%d/%s/%s-v1.jpg".formatted(
                file.getId(), version(file.getId(), file.getSha256()), variant.path);
    }

    private String derivedName(FileAssetRecord file, Variant variant) {
        String name = file.getOriginalName() == null ? "product" : file.getOriginalName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name + "-" + variant.path + ".jpg";
    }

    private String url(long fileId, Variant variant, String version) {
        return "%s/%d/%s/%s".formatted(URL_PREFIX, fileId, variant.path, version);
    }

    private String version(long fileId, String sha256) {
        if (sha256 != null && sha256.matches("(?i)[0-9a-f]{64}")) return sha256.toLowerCase(Locale.ROOT);
        return "legacy-" + fileId;
    }

    private String ensureSha256(long fileId, String sha256) {
        if (sha256 != null && sha256.matches("(?i)[0-9a-f]{64}")) return sha256;
        FileAssetRecord file = fileMapper.selectById(fileId);
        if (file == null || file.getObjectKey() == null || file.getObjectKey().isBlank()) return sha256;
        try (InputStream input = storage.load(file.getObjectKey()).getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestOutputStream output = new DigestOutputStream(OutputStream.nullOutputStream(), digest)) {
                input.transferTo(output);
            }
            String calculated = HexFormat.of().formatHex(digest.digest());
            file.setSha256(calculated);
            fileMapper.updateById(file);
            return calculated;
        } catch (Exception exception) {
            log.warn("Product image hash backfill skipped, fileId={}, cause={}",
                    fileId, exception.getClass().getSimpleName());
            return sha256;
        }
    }

    private String quote(String value) { return "\"" + value + "\""; }

    private BusinessException notFound() {
        return new BusinessException(404, "PRODUCT_IMAGE_NOT_FOUND", "产品图片不存在");
    }

    enum Variant {
        CARD("card", 640, 300L * 1024),
        DISPLAY("display", 1600, 1024L * 1024),
        ORIGINAL("original", Integer.MAX_VALUE, Long.MAX_VALUE);

        private final String path;
        private final int maxEdge;
        private final long targetBytes;

        Variant(String path, int maxEdge, long targetBytes) {
            this.path = path;
            this.maxEdge = maxEdge;
            this.targetBytes = targetBytes;
        }

        static Variant parse(String value) {
            for (Variant variant : values()) if (variant.path.equalsIgnoreCase(value)) return variant;
            throw new BusinessException(404, "PRODUCT_IMAGE_NOT_FOUND", "产品图片不存在");
        }
    }

    public record ProductImageUrls(String cardUrl, String displayUrl, String originalUrl) { }
    public record PublicImageContent(Resource resource, String mimeType, long size, String etag,
                                     String filename) { }
}
