package com.lczz.file.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lczz.file")
public class FileStorageProperties {
    private String storageType = "LOCAL";
    private String localRoot = "./data/uploads";
    private long maxBytes = 200L * 1024 * 1024;
    private long maxImageBytes = 10L * 1024 * 1024;
    private long signedUrlMinutes = 60;
    private String accessSecret;

    public String getStorageType() { return storageType; }
    public void setStorageType(String storageType) { this.storageType = storageType; }
    public String getLocalRoot() { return localRoot; }
    public void setLocalRoot(String localRoot) { this.localRoot = localRoot; }
    public long getMaxBytes() { return maxBytes; }
    public void setMaxBytes(long maxBytes) { this.maxBytes = maxBytes; }
    public long getMaxImageBytes() { return maxImageBytes; }
    public void setMaxImageBytes(long maxImageBytes) { this.maxImageBytes = maxImageBytes; }
    public long getSignedUrlMinutes() { return signedUrlMinutes; }
    public void setSignedUrlMinutes(long signedUrlMinutes) { this.signedUrlMinutes = signedUrlMinutes; }
    public String getAccessSecret() { return accessSecret; }
    public void setAccessSecret(String accessSecret) { this.accessSecret = accessSecret; }
}
