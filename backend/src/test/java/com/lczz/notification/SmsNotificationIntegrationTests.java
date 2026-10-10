package com.lczz.notification;

import com.lczz.notification.config.SmsProperties;
import com.lczz.notification.service.SmsNotificationService;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class SmsNotificationIntegrationTests {
    @Autowired SmsNotificationService smsNotificationService;
    @Autowired SmsProperties smsProperties;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM sms_notification");
        smsProperties.setEnabled(false);
        smsProperties.setAccessKeyId(null);
        smsProperties.setAccessKeySecret(null);
        smsProperties.setSignName(null);
        smsProperties.setInstallerAssignmentTemplateCode(null);
        smsProperties.setDealerAppointmentTemplateCode(null);
        smsProperties.setSelfOrderCreatedTemplateCode(null);
        smsProperties.setAdminPhones(null);
        smsProperties.setDealerAppointmentAdminPhones(null);
        smsProperties.setSelfOrderAdminPhones(null);
    }

    @AfterEach
    void clearData() {
        resetData();
    }

    @Test
    void disabledSmsIsAuditedAndSameAssignmentIsIdempotent() {
        smsNotificationService.queueInstallerAssignment(101L, 5001L, "WO202609040001", "13900000001");
        smsNotificationService.queueInstallerAssignment(101L, 5001L, "WO202609040001", "13900000001");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT notification_status FROM sms_notification", String.class)).isEqualTo("SKIPPED");
        assertThat(jdbcTemplate.queryForObject("SELECT last_error FROM sms_notification", String.class))
                .isEqualTo("SMS_DISABLED");
    }

    @Test
    void missingProviderConfigurationIsAuditedWithoutCallingExternalService() {
        smsProperties.setEnabled(true);
        smsProperties.setInstallerAssignmentTemplateCode("SMS_TEST");
        smsNotificationService.queueInstallerAssignment(102L, 5002L, "WO202609040002", "13900000002");

        assertThat(jdbcTemplate.queryForObject("SELECT notification_status FROM sms_notification", String.class))
                .isEqualTo("SKIPPED");
        assertThat(jdbcTemplate.queryForObject("SELECT last_error FROM sms_notification", String.class))
                .isEqualTo("SMS_PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void disabledSelfOrderNotificationIsAuditedOncePerAdministratorAndIdempotent() {
        smsProperties.setAdminPhones("13900000003,+8613900000004,invalid");

        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForList(
                "SELECT event_type FROM sms_notification ORDER BY id", String.class))
                .containsOnly("INSTALLER_SELF_ORDER_CREATED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT business_type FROM sms_notification ORDER BY id", String.class))
                .containsOnly("MATERIAL_SELF_ORDER");
        assertThat(jdbcTemplate.queryForList(
                "SELECT notification_status FROM sms_notification ORDER BY id", String.class))
                .containsOnly("SKIPPED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT last_error FROM sms_notification ORDER BY id", String.class))
                .containsOnly("SMS_DISABLED");
        assertThat(jdbcTemplate.queryForList(
                "SELECT template_params_json FROM sms_notification ORDER BY id", String.class))
                .allMatch(params -> params.contains("A202609150001"));
    }

    @Test
    void dedicatedRecipientsAreIsolatedFromEachOtherAndLegacyAdministrators() {
        smsProperties.setAdminPhones("13900000001,13900000002");
        smsProperties.setSelfOrderAdminPhones("13900000003");
        smsProperties.setDealerAppointmentAdminPhones("13900000004");
        smsProperties.setSelfOrderCreatedTemplateCode("SMS_SELF_ORDER");
        smsProperties.setDealerAppointmentTemplateCode("SMS_APPOINTMENT");
        smsProperties.setInstallerAssignmentTemplateCode("SMS_ASSIGNMENT");

        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueDealerAppointmentCreated(302L, "WO202609150002");
        smsNotificationService.queueInstallerAssignment(303L, 5003L, "WO202609150003", "13900000005");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Long.class)).isEqualTo(3L);
        assertThat(recipients("INSTALLER_SELF_ORDER_CREATED")).containsExactly("13900000003");
        assertThat(recipients("DEALER_APPOINTMENT_CREATED")).containsExactly("13900000004");
        assertThat(recipients("INSTALLER_ASSIGNED")).containsExactly("13900000005");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT template_code FROM sms_notification WHERE event_type = 'INSTALLER_SELF_ORDER_CREATED'",
                String.class)).isEqualTo("SMS_SELF_ORDER");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT template_code FROM sms_notification WHERE event_type = 'DEALER_APPOINTMENT_CREATED'",
                String.class)).isEqualTo("SMS_APPOINTMENT");
    }

    @Test
    void unconfiguredAppointmentRecipientsFallBackWithoutAffectingDedicatedSelfOrderRecipients() {
        smsProperties.setAdminPhones("13900000001,13900000002");
        smsProperties.setSelfOrderAdminPhones("13900000003");

        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueDealerAppointmentCreated(302L, "WO202609150002");

        assertThat(recipients("INSTALLER_SELF_ORDER_CREATED")).containsExactly("13900000003");
        assertThat(recipients("DEALER_APPOINTMENT_CREATED")).containsExactly("13900000001", "13900000002");
    }

    @Test
    void blankDedicatedRecipientsPreserveLegacyDeliveryForBothScenarios() {
        smsProperties.setAdminPhones("13900000001,+8613900000002");
        smsProperties.setSelfOrderAdminPhones("");
        smsProperties.setDealerAppointmentAdminPhones("   ");

        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueDealerAppointmentCreated(302L, "WO202609150002");

        assertThat(recipients("INSTALLER_SELF_ORDER_CREATED")).containsExactly("13900000001", "13900000002");
        assertThat(recipients("DEALER_APPOINTMENT_CREATED")).containsExactly("13900000001", "13900000002");
    }

    @Test
    void dedicatedRecipientsAreNormalizedAndDeduplicatedWithoutInvalidNumbersFallingBack() {
        smsProperties.setAdminPhones("13900000001");
        smsProperties.setSelfOrderAdminPhones(" 13900000003, +8613900000003, , 139-0000-0004, invalid ");
        smsProperties.setDealerAppointmentAdminPhones("invalid");

        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueInstallerSelfOrderCreated(301L, "A202609150001");
        smsNotificationService.queueDealerAppointmentCreated(302L, "WO202609150002");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sms_notification", Long.class)).isEqualTo(2L);
        assertThat(recipients("INSTALLER_SELF_ORDER_CREATED")).containsExactly("13900000003", "13900000004");
        assertThat(recipients("DEALER_APPOINTMENT_CREATED")).isEmpty();
    }

    private List<String> recipients(String eventType) {
        return jdbcTemplate.queryForList(
                "SELECT recipient_phone FROM sms_notification WHERE event_type = ? ORDER BY recipient_phone",
                String.class, eventType);
    }
}
