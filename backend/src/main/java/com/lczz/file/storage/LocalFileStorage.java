package com.lczz.file.storage;

import com.lczz.common.exception.BusinessException;
import com.lczz.file.config.FileStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "lczz.file", name = "storage-type", havingValue = "LOCAL", matchIfMissing = true)
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(FileStorageProperties properties) {
        this.root = Path.of(properties.getLocalRoot()).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialize() throws IOException {
        Files.createDirectories(root);
    }

    @Override
    public String storageType() { return "LOCAL"; }

    @Override
    public long store(String objectKey, InputStream content) throws IOException {
        Path target = resolve(objectKey);
        Files.createDirectories(target.getParent());
        try (OutputStream output = Files.newOutputStream(
                target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            return content.transferTo(output);
        }
    }

    @Override
    public Resource load(String objectKey) {
        Path target = resolve(objectKey);
        if (!Files.isRegularFile(target)) {
            throw new BusinessException(404, "FILE_CONTENT_NOT_FOUND", "文件内容不存在");
        }
        return new FileSystemResource(target);
    }

    @Override
    public void deleteQuietly(String objectKey) {
        try { Files.deleteIfExists(resolve(objectKey)); }
        catch (IOException ignored) { }
    }

    private Path resolve(String objectKey) {
        Path target = root.resolve(objectKey).normalize();
        if (!target.startsWith(root)) throw new BusinessException(400, "INVALID_OBJECT_KEY", "文件对象键不合法");
        return target;
    }
}
