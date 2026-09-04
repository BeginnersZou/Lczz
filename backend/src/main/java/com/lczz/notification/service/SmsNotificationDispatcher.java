package com.lczz.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lczz.notification.config.SmsProperties;
import com.lczz.notification.persistence.SmsNotificationEntity;
import com.lczz.notification.persistence.SmsNotificationMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SmsNotificationDispatcher {
    private static final Logger log = LoggerFactory.getLogger(SmsNotificationDispatcher.class);
    private final SmsNotificationMapper mapper;
    private final SmsProperties properties;
    private final SmsGateway gateway;

    public SmsNotificationDispatcher(SmsNotificationMapper mapper, SmsProperties properties, SmsGateway gateway) {
        this.mapper = mapper;
        this.properties = properties;
        this.gateway = gateway;
    }

    @Async("smsTaskExecutor")
    public void dispatchAsync(long notificationId) {
        deliver(notificationId);
    }

    /** Runs after commit, so provider failures cannot roll back an order or assignment. */
    @Transactional
    public void deliver(long notificationId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (!claim(notificationId, now)) return;
        SmsNotificationEntity notification = mapper.selectById(notificationId);
        if (notification == null) return;
        SmsGateway.SendResult result;
        try {
            result = gateway.send(new SmsGateway.SmsMessage(notification.getRecipientPhone(),
                    properties.getSignName(), notification.getTemplateCode(), notification.getTemplateParamsJson()));
        } catch (RuntimeException exception) {
            log.warn("SMS gateway threw unexpectedly: notificationId={}, exceptionType={}", notificationId,
                    exception.getClass().getSimpleName());
            result = SmsGateway.SendResult.failed("SMS_GATEWAY_FAILURE", "短信服务调用失败", null);
        }
        if (result != null && result.accepted()) {
            mapper.update(null, new LambdaUpdateWrapper<SmsNotificationEntity>()
                    .eq(SmsNotificationEntity::getId, notificationId)
                    .eq(SmsNotificationEntity::getNotificationStatus, "SENDING")
                    .set(SmsNotificationEntity::getNotificationStatus, "SENT")
                    .set(SmsNotificationEntity::getSentAt, now)
                    .set(SmsNotificationEntity::getNextAttemptAt, null)
                    .set(SmsNotificationEntity::getProviderCode, trim(result.providerCode(), 64))
                    .set(SmsNotificationEntity::getProviderRequestId, trim(result.providerRequestId(), 128))
                    .set(SmsNotificationEntity::getProviderBizId, trim(result.providerBizId(), 128))
                    .set(SmsNotificationEntity::getLastError, null));
            return;
        }
        int attempts = notification.getAttemptCount() == null ? 1 : notification.getAttemptCount();
        boolean canRetry = attempts < properties.getMaxAttempts();
        mapper.update(null, new LambdaUpdateWrapper<SmsNotificationEntity>()
                .eq(SmsNotificationEntity::getId, notificationId)
                .eq(SmsNotificationEntity::getNotificationStatus, "SENDING")
                .set(SmsNotificationEntity::getNotificationStatus, "FAILED")
                .set(SmsNotificationEntity::getNextAttemptAt,
                        canRetry ? now.plus(Duration.ofMillis(properties.getRetryDelayMs())) : null)
                .set(SmsNotificationEntity::getProviderCode, trim(result == null ? null : result.providerCode(), 64))
                .set(SmsNotificationEntity::getProviderRequestId,
                        trim(result == null ? null : result.providerRequestId(), 128))
                .set(SmsNotificationEntity::getProviderBizId, trim(result == null ? null : result.providerBizId(), 128))
                .set(SmsNotificationEntity::getLastError,
                        trim(result == null ? "SMS_EMPTY_RESULT" : result.providerMessage(), 1000)));
        log.warn("SMS delivery failed: notificationId={}, eventType={}, retryable={}", notificationId,
                notification.getEventType(), canRetry);
    }

    @Scheduled(fixedDelayString = "${lczz.sms.retry-delay-ms:60000}")
    public void retryFailedNotifications() {
        if (!properties.isEnabled()) return;
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Page<SmsNotificationEntity> page = mapper.selectPage(new Page<>(1, 20),
                new LambdaQueryWrapper<SmsNotificationEntity>()
                        .eq(SmsNotificationEntity::getNotificationStatus, "FAILED")
                        .lt(SmsNotificationEntity::getAttemptCount, properties.getMaxAttempts())
                        .le(SmsNotificationEntity::getNextAttemptAt, now)
                        .orderByAsc(SmsNotificationEntity::getNextAttemptAt));
        for (SmsNotificationEntity notification : page.getRecords()) {
            deliver(notification.getId());
        }
    }

    private boolean claim(long notificationId, LocalDateTime now) {
        return mapper.update(null, new LambdaUpdateWrapper<SmsNotificationEntity>()
                .eq(SmsNotificationEntity::getId, notificationId)
                .in(SmsNotificationEntity::getNotificationStatus, "PENDING", "FAILED")
                .lt(SmsNotificationEntity::getAttemptCount, properties.getMaxAttempts())
                .and(wrapper -> wrapper.isNull(SmsNotificationEntity::getNextAttemptAt)
                        .or().le(SmsNotificationEntity::getNextAttemptAt, now))
                .set(SmsNotificationEntity::getNotificationStatus, "SENDING")
                .set(SmsNotificationEntity::getLastAttemptAt, now)
                .setSql("attempt_count = attempt_count + 1")) == 1;
    }

    private String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
