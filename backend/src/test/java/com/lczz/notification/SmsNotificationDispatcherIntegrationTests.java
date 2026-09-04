package com.lczz.notification;

import com.lczz.notification.persistence.SmsNotificationEntity;
import com.lczz.notification.persistence.SmsNotificationMapper;
import com.lczz.notification.service.SmsGateway;
import com.lczz.notification.service.SmsNotificationDispatcher;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
        "lczz.sms.enabled=true",
        "lczz.sms.max-attempts=2",
        "lczz.sms.retry-delay-ms=3600000"
})
@ActiveProfiles("test")
class SmsNotificationDispatcherIntegrationTests {
    @Autowired SmsNotificationMapper mapper;
    @Autowired SmsNotificationDispatcher dispatcher;
    @MockitoBean SmsGateway smsGateway;

    @BeforeEach
    void resetData() {
        mapper.delete(null);
    }

    @Test
    void acceptedProviderResponseMarksNotificationAsSent() {
        SmsNotificationEntity notification = pending("SMS-SUCCESS");
        when(smsGateway.send(any())).thenReturn(SmsGateway.SendResult.accepted("OK", "request-1", "biz-1"));

        dispatcher.deliver(notification.getId());

        SmsNotificationEntity saved = mapper.selectById(notification.getId());
        assertThat(saved.getNotificationStatus()).isEqualTo("SENT");
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getProviderRequestId()).isEqualTo("request-1");
        assertThat(saved.getProviderBizId()).isEqualTo("biz-1");
        assertThat(saved.getSentAt()).isNotNull();
    }

    @Test
    void failedProviderResponseIsRecordedForControlledRetry() {
        SmsNotificationEntity notification = pending("SMS-FAILURE");
        when(smsGateway.send(any())).thenReturn(
                SmsGateway.SendResult.failed("isv.BUSINESS_LIMIT_CONTROL", "发送频率受限", "request-2"));

        dispatcher.deliver(notification.getId());

        SmsNotificationEntity saved = mapper.selectById(notification.getId());
        assertThat(saved.getNotificationStatus()).isEqualTo("FAILED");
        assertThat(saved.getAttemptCount()).isEqualTo(1);
        assertThat(saved.getProviderCode()).isEqualTo("isv.BUSINESS_LIMIT_CONTROL");
        assertThat(saved.getLastError()).isEqualTo("发送频率受限");
        assertThat(saved.getNextAttemptAt()).isNotNull();
    }

    private SmsNotificationEntity pending(String suffix) {
        SmsNotificationEntity notification = new SmsNotificationEntity();
        notification.setEventKey("INSTALLER_ASSIGNED:WORK_ORDER:10:" + suffix + ":13900000001");
        notification.setEventType("INSTALLER_ASSIGNED");
        notification.setBusinessType("WORK_ORDER");
        notification.setBusinessId("10");
        notification.setRecipientPhone("13900000001");
        notification.setTemplateCode("SMS_TEST");
        notification.setTemplateParamsJson("{\"orderNo\":\"WO_TEST\"}");
        notification.setNotificationStatus("PENDING");
        notification.setAttemptCount(0);
        notification.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        notification.setUpdatedAt(LocalDateTime.now(ZoneOffset.UTC));
        mapper.insert(notification);
        return notification;
    }
}
