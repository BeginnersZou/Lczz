package com.lczz.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.notification.config.SmsProperties;
import com.lczz.notification.persistence.SmsNotificationEntity;
import com.lczz.notification.persistence.SmsNotificationMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class SmsNotificationService {
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");
    static final String INSTALLER_ASSIGNED = "INSTALLER_ASSIGNED";
    static final String DEALER_APPOINTMENT_CREATED = "DEALER_APPOINTMENT_CREATED";

    private final SmsNotificationMapper mapper;
    private final SmsProperties properties;
    private final ObjectMapper objectMapper;
    private final SmsNotificationDispatcher dispatcher;

    public SmsNotificationService(SmsNotificationMapper mapper, SmsProperties properties,
                                  ObjectMapper objectMapper, SmsNotificationDispatcher dispatcher) {
        this.mapper = mapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.dispatcher = dispatcher;
    }

    /** Queue a message only after a successful first assignment or reassignment transaction. */
    public void queueInstallerAssignment(long orderId, long assignmentId, String orderNo, String installerPhone) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("orderNo", orderNo);
        queue(new NotificationCommand(INSTALLER_ASSIGNED, "WORK_ORDER", Long.toString(orderId),
                Long.toString(assignmentId), installerPhone, properties.getInstallerAssignmentTemplateCode(), params));
    }

    /** Public contract for #100. Each configured administrator receives one idempotent appointment notification. */
    public void queueDealerAppointmentCreated(long appointmentId, String appointmentNo) {
        Map<String, String> params = Map.of("orderNo", appointmentNo);
        for (String adminPhone : properties.adminPhoneList()) {
            queue(new NotificationCommand(DEALER_APPOINTMENT_CREATED, "DEALER_APPOINTMENT",
                    Long.toString(appointmentId), appointmentNo, adminPhone,
                    properties.getDealerAppointmentTemplateCode(), params));
        }
    }

    private void queue(NotificationCommand command) {
        String phone = normalizePhone(command.recipientPhone());
        if (phone == null) return;
        String eventKey = eventKey(command, phone);
        SmsNotificationEntity existing = mapper.selectOne(new LambdaQueryWrapper<SmsNotificationEntity>()
                .eq(SmsNotificationEntity::getEventKey, eventKey));
        if (existing != null) return;

        SmsNotificationEntity notification = new SmsNotificationEntity();
        notification.setEventKey(eventKey);
        notification.setEventType(command.eventType());
        notification.setBusinessType(command.businessType());
        notification.setBusinessId(command.businessId());
        notification.setRecipientPhone(phone);
        notification.setTemplateCode(blankToNull(command.templateCode()));
        notification.setTemplateParamsJson(toJson(command.templateParams()));
        notification.setAttemptCount(0);
        notification.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notification.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notification.setNotificationStatus(initialStatus(notification));
        notification.setLastError(initialReason(notification));
        try {
            mapper.insert(notification);
        } catch (DuplicateKeyException ignored) {
            return;
        }
        if ("PENDING".equals(notification.getNotificationStatus())) {
            dispatchAfterCommit(notification.getId());
        }
    }

    private void dispatchAfterCommit(long notificationId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    dispatcher.dispatchAsync(notificationId);
                }
            });
            return;
        }
        dispatcher.dispatchAsync(notificationId);
    }

    private String initialStatus(SmsNotificationEntity notification) {
        if (!properties.isEnabled() || !SmsProperties.hasText(notification.getTemplateCode())
                || !properties.hasProviderCredentials()) return "SKIPPED";
        return "PENDING";
    }

    private String initialReason(SmsNotificationEntity notification) {
        if (!properties.isEnabled()) return "SMS_DISABLED";
        if (!SmsProperties.hasText(notification.getTemplateCode())) return "SMS_TEMPLATE_NOT_CONFIGURED";
        if (!properties.hasProviderCredentials()) return "SMS_PROVIDER_NOT_CONFIGURED";
        return null;
    }

    private String eventKey(NotificationCommand command, String phone) {
        String value = command.eventType() + ":" + command.businessType() + ":" + command.businessId()
                + ":" + command.eventReference() + ":" + phone;
        return value.length() <= 160 ? value : Integer.toHexString(value.hashCode());
    }

    private String toJson(Map<String, String> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize SMS template parameters", exception);
        }
    }

    private String normalizePhone(String raw) {
        if (!SmsProperties.hasText(raw)) return null;
        String value = raw.trim().replace(" ", "").replace("-", "");
        if (value.startsWith("+86")) value = value.substring(3);
        return PHONE_PATTERN.matcher(value).matches() ? value : null;
    }

    private String blankToNull(String value) {
        return SmsProperties.hasText(value) ? value.trim() : null;
    }

    private record NotificationCommand(String eventType, String businessType, String businessId,
                                       String eventReference, String recipientPhone, String templateCode,
                                       Map<String, String> templateParams) { }
}
