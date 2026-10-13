package com.lczz.notification.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lczz.sms")
public class SmsProperties {
    private boolean enabled;
    private String accessKeyId;
    private String accessKeySecret;
    private String signName;
    private String dealerAppointmentTemplateCode;
    private String installerAssignmentTemplateCode;
    private String selfOrderCreatedTemplateCode;
    private String progressSubmittedTemplateCode;
    private String adminPhones;
    private String dealerAppointmentAdminPhones;
    private String selfOrderAdminPhones;
    private String pickupContactPhone;
    private int maxAttempts = 3;
    private long retryDelayMs = 60_000L;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getAccessKeySecret() { return accessKeySecret; }
    public void setAccessKeySecret(String accessKeySecret) { this.accessKeySecret = accessKeySecret; }
    public String getSignName() { return signName; }
    public void setSignName(String signName) { this.signName = signName; }
    public String getDealerAppointmentTemplateCode() { return dealerAppointmentTemplateCode; }
    public void setDealerAppointmentTemplateCode(String dealerAppointmentTemplateCode) {
        this.dealerAppointmentTemplateCode = dealerAppointmentTemplateCode;
    }
    public String getInstallerAssignmentTemplateCode() { return installerAssignmentTemplateCode; }
    public void setInstallerAssignmentTemplateCode(String installerAssignmentTemplateCode) {
        this.installerAssignmentTemplateCode = installerAssignmentTemplateCode;
    }
    public String getSelfOrderCreatedTemplateCode() { return selfOrderCreatedTemplateCode; }
    public void setSelfOrderCreatedTemplateCode(String selfOrderCreatedTemplateCode) {
        this.selfOrderCreatedTemplateCode = selfOrderCreatedTemplateCode;
    }
    public String getProgressSubmittedTemplateCode() { return progressSubmittedTemplateCode; }
    public void setProgressSubmittedTemplateCode(String value) { progressSubmittedTemplateCode = value; }
    public String getAdminPhones() { return adminPhones; }
    public void setAdminPhones(String adminPhones) { this.adminPhones = adminPhones; }
    public String getDealerAppointmentAdminPhones() { return dealerAppointmentAdminPhones; }
    public void setDealerAppointmentAdminPhones(String dealerAppointmentAdminPhones) {
        this.dealerAppointmentAdminPhones = dealerAppointmentAdminPhones;
    }
    public String getSelfOrderAdminPhones() { return selfOrderAdminPhones; }
    public void setSelfOrderAdminPhones(String selfOrderAdminPhones) { this.selfOrderAdminPhones = selfOrderAdminPhones; }
    public String getPickupContactPhone() { return pickupContactPhone; }
    public void setPickupContactPhone(String pickupContactPhone) { this.pickupContactPhone = pickupContactPhone; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = Math.max(1, maxAttempts); }
    public long getRetryDelayMs() { return retryDelayMs; }
    public void setRetryDelayMs(long retryDelayMs) { this.retryDelayMs = Math.max(1_000L, retryDelayMs); }

    public boolean hasProviderCredentials() {
        return hasText(accessKeyId) && hasText(accessKeySecret) && hasText(signName);
    }

    public List<String> dealerAppointmentAdminPhoneList() {
        return phoneList(hasText(dealerAppointmentAdminPhones) ? dealerAppointmentAdminPhones : adminPhones);
    }

    public List<String> selfOrderAdminPhoneList() {
        return phoneList(hasText(selfOrderAdminPhones) ? selfOrderAdminPhones : adminPhones);
    }

    private static List<String> phoneList(String phones) {
        if (!hasText(phones)) return List.of();
        return Arrays.stream(phones.split(","))
                .map(String::trim)
                .filter(SmsProperties::hasText)
                .toList();
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
