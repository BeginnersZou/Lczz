package com.lczz.notification.service;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.teaopenapi.models.Config;
import com.lczz.notification.config.SmsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Uses Alibaba Cloud's supported Dysmsapi V2 Java SDK. */
@Component
public class AliyunSmsGateway implements SmsGateway {
    private static final Logger log = LoggerFactory.getLogger(AliyunSmsGateway.class);
    private final SmsProperties properties;

    public AliyunSmsGateway(SmsProperties properties) {
        this.properties = properties;
    }

    @Override
    public SendResult send(SmsMessage message) {
        if (!properties.hasProviderCredentials()) {
            return SendResult.failed("SMS_PROVIDER_NOT_CONFIGURED", "短信服务未配置", null);
        }
        try {
            SendSmsResponse response = createClient().sendSms(new SendSmsRequest()
                    .setPhoneNumbers(message.recipientPhone())
                    .setSignName(message.signName())
                    .setTemplateCode(message.templateCode())
                    .setTemplateParam(message.templateParamsJson()));
            if (response == null || response.getBody() == null) {
                return SendResult.failed("SMS_EMPTY_RESPONSE", "短信服务返回为空", null);
            }
            String code = response.getBody().getCode();
            String requestId = response.getBody().getRequestId();
            if ("OK".equalsIgnoreCase(code)) {
                return SendResult.accepted(code, requestId, response.getBody().getBizId());
            }
            return SendResult.failed(code, response.getBody().getMessage(), requestId);
        } catch (Exception exception) {
            // Never include AccessKey, template parameters, or a full phone number in logs.
            log.warn("Aliyun SMS transport failure: exceptionType={}", exception.getClass().getSimpleName());
            return SendResult.failed("SMS_TRANSPORT_FAILURE", "短信服务调用失败", null);
        }
    }

    private Client createClient() throws Exception {
        Config config = new Config()
                .setAccessKeyId(properties.getAccessKeyId().trim())
                .setAccessKeySecret(properties.getAccessKeySecret().trim())
                .setEndpoint("dysmsapi.aliyuncs.com")
                .setRegionId("cn-hangzhou");
        return new Client(config);
    }
}
