package com.lczz.file.storage;

import java.io.IOException;
import org.springframework.core.io.Resource;

public interface FileStorage {
    String storageType();
    void store(String objectKey, byte[] content) throws IOException;
    Resource load(String objectKey);
    void deleteQuietly(String objectKey);
}
