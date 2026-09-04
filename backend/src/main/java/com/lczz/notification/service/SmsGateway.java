package com.lczz.notification.service;

/**
 * Outbound SMS provider boundary. Business code only records and dispatches notifications through this contract.
 */
public interface SmsGateway {
    SendResult send(SmsMessage message);

    record SmsMessage(String recipientPhone, String signName, String templateCode, String templateParamsJson) { }

    record SendResult(boolean accepted, String providerCode, String providerMessage,
                      String providerRequestId, String providerBizId) {
        public static SendResult accepted(String providerCode, String providerRequestId, String providerBizId) {
            return new SendResult(true, providerCode, null, providerRequestId, providerBizId);
        }

        public static SendResult failed(String providerCode, String providerMessage, String providerRequestId) {
            return new SendResult(false, providerCode, providerMessage, providerRequestId, null);
        }
    }
}
