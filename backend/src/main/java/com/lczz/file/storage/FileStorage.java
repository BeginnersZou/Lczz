package com.lczz.file.storage;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface FileStorage {
    String storageType();
    long store(String objectKey, InputStream content) throws IOException;
    Resource load(String objectKey);
    void deleteQuietly(String objectKey);
}
