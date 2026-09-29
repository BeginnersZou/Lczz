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
import org.springframework.stereotype.Service;

/** Creates cached, bounded-size previews for private/signed business images. */
@Service
public class FileImageVariantService {
    private static final Logger log = LoggerFactory.getLogger(FileImageVariantService.class);
    private static final String JPEG_MIME = "image/jpeg";

    private final FileAssetRecordMapper fileMapper;
    private final FileStorage storage;

    public FileImageVariantService(FileAssetRecordMapper fileMapper, FileStorage storage) {
        this.fileMapper = fileMapper;
        this.storage = storage;
    }

    public ImageContent content(long fileId, String rawVariant) {
        Variant variant = Variant.parse(rawVariant);
        FileAssetRecord file = fileMapper.selectById(fileId);
        if (file == null || Boolean.TRUE.equals(file.getDeleted()) || file.getObjectKey() == null
                || file.getMimeType() == null || !file.getMimeType().startsWith("image/")) {
            throw new BusinessException(404, "FILE_IMAGE_NOT_FOUND", "图片不存在");
        }
        Resource original = storage.load(file.getObjectKey());
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
            log.warn("File image derivative fallback, fileId={}, variant={}, cause={}",
                    fileId, variant, exception.getClass().getSimpleName());
            return original(file, original);
        }
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

    private ImageContent original(FileAssetRecord file, Resource resource) {
        return new ImageContent(resource, file.getMimeType(), length(resource), quote(fileEtag(file)),
                file.getOriginalName());
    }

    private ImageContent derived(FileAssetRecord file, Variant variant, Resource resource) {
        return new ImageContent(resource, JPEG_MIME, length(resource),
                quote(fileEtag(file) + "-" + variant.path + "-v1"), derivedName(file, variant));
    }

    private long length(Resource resource) {
        try {
            return resource.contentLength();
        } catch (IOException exception) {
            throw new BusinessException(500, "FILE_IMAGE_READ_FAILED", "图片读取失败");
        }
    }

    private String fileEtag(FileAssetRecord file) {
        return file.getSha256() == null || file.getSha256().isBlank()
                ? "legacy-" + file.getId() : file.getSha256().toLowerCase(Locale.ROOT);
    }

    private String derivedKey(FileAssetRecord file, Variant variant) {
        return "variants/file/%d/%s/%s-v1.jpg".formatted(
                file.getId(), fileEtag(file), variant.path);
    }

    private String derivedName(FileAssetRecord file, Variant variant) {
        String name = file.getOriginalName() == null ? "image" : file.getOriginalName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name + "-" + variant.path + ".jpg";
    }

    private String quote(String value) { return "\"" + value + "\""; }

    enum Variant {
        THUMBNAIL("thumbnail", 640, 300L * 1024),
        DISPLAY("display", 1600, 1024L * 1024);

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
            throw new BusinessException(404, "FILE_IMAGE_NOT_FOUND", "图片规格不存在");
        }
    }

    public record ImageContent(Resource resource, String mimeType, long size, String etag, String filename) { }
}
