package com.lczz.auth.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.config.WechatMiniProperties;
import com.lczz.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class WechatApiClient implements WechatIdentityGateway {
    private static final Logger log = LoggerFactory.getLogger(WechatApiClient.class);
    private static final String CODE_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";
    private static final String USER_AGENT = "LCZZ-Backend/0.1";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WechatMiniProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public WechatApiClient(RestClient restClient, ObjectMapper objectMapper,
                           WechatMiniProperties properties, Clock clock) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WechatIdentity exchangeLoginCode(String code) {
        requireConfiguration();
        try {
            String responseBody = restClient.get().uri(CODE_SESSION_URL, builder -> builder
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code").build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .retrieve().body(String.class);
            CodeSessionResponse response = decode("code-session", responseBody, CodeSessionResponse.class);
            if (response == null || response.errorCode() != null && response.errorCode() != 0
                    || response.openId() == null || response.openId().isBlank()) {
                throw wechatFailure(response == null ? null : response.errorMessage());
            }
            return new WechatIdentity(properties.appId(), response.openId(), response.unionId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("code-session", exception);
        }
    }

    @Override
    public String exchangePhoneCode(String phoneCode) {
        requireConfiguration();
        try {
            byte[] requestBody = encodePhoneRequest(phoneCode);
            String responseBody = restClient.post().uri(PHONE_URL, builder -> builder
                            .queryParam("access_token", accessToken()).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .contentType(MediaType.APPLICATION_JSON)
                    .contentLength(requestBody.length)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .body(requestBody).retrieve().body(String.class);
            PhoneResponse response = decode("phone-number", responseBody, PhoneResponse.class);
            if (response == null || response.errorCode() != null && response.errorCode() != 0
                    || response.phoneInfo() == null || response.phoneInfo().phoneNumber() == null) {
                throw wechatFailure(response == null ? null : response.errorMessage());
            }
            return response.phoneInfo().phoneNumber();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("phone-number", exception);
        }
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(60))) {
            return cachedToken.value();
        }
        try {
            String responseBody = restClient.get().uri(TOKEN_URL, builder -> builder
                            .queryParam("grant_type", "client_credential")
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret()).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.USER_AGENT, USER_AGENT)
                    .retrieve().body(String.class);
            TokenResponse response = decode("access-token", responseBody, TokenResponse.class);
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw wechatFailure(response == null ? null : response.errorMessage());
            }
            long seconds = response.expiresIn() == null ? 7200 : response.expiresIn();
            cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(seconds));
            return cachedToken.value();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable("access-token", exception);
        }
    }

    private byte[] encodePhoneRequest(String phoneCode) {
        try {
            return objectMapper.writeValueAsBytes(Map.of("code", phoneCode));
        } catch (JsonProcessingException exception) {
            log.error("Failed to encode WeChat phone-number request", exception);
            throw new BusinessException(500, "WECHAT_REQUEST_ENCODING_FAILED", "微信手机号请求生成失败");
        }
    }

    private void requireConfiguration() {
        if (!properties.configured()) {
            throw new BusinessException(503, "WECHAT_NOT_CONFIGURED", "微信小程序认证尚未配置");
        }
    }

    private BusinessException wechatFailure(String detail) {
        String message = detail == null || detail.isBlank() ? "微信凭证无效或已过期" : "微信凭证校验失败";
        return new BusinessException(400, "WECHAT_AUTH_FAILED", message);
    }

    private <T> T decode(String operation, String responseBody, Class<T> responseType) {
        if (responseBody == null || responseBody.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readerFor(responseType)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(responseBody);
        } catch (JsonProcessingException exception) {
            log.warn("WeChat API returned invalid JSON: operation={}, exceptionType={}",
                    operation, exception.getClass().getSimpleName());
            throw new BusinessException(502, "WECHAT_UNAVAILABLE", "微信服务暂不可用，请稍后重试");
        }
    }

    private BusinessException unavailable(String operation, RestClientException exception) {
        Integer httpStatus = exception instanceof RestClientResponseException response
                ? response.getStatusCode().value() : null;
        log.warn("WeChat API transport failure: operation={}, exceptionType={}, causeType={}, httpStatus={}",
                operation, exception.getClass().getSimpleName(), rootCauseType(exception), httpStatus);
        return new BusinessException(502, "WECHAT_UNAVAILABLE", "微信服务暂不可用，请稍后重试");
    }

    private String rootCauseType(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return root.getClass().getSimpleName();
    }

    private record CachedToken(String value, Instant expiresAt) { }
    private record CodeSessionResponse(@JsonProperty("openid") String openId,
                                       @JsonProperty("unionid") String unionId,
                                       @JsonProperty("errcode") Integer errorCode,
                                       @JsonProperty("errmsg") String errorMessage) { }
    private record TokenResponse(@JsonProperty("access_token") String accessToken,
                                 @JsonProperty("expires_in") Long expiresIn,
                                 @JsonProperty("errcode") Integer errorCode,
                                 @JsonProperty("errmsg") String errorMessage) { }
    private record PhoneResponse(@JsonProperty("errcode") Integer errorCode,
                                 @JsonProperty("errmsg") String errorMessage,
                                 @JsonProperty("phone_info") PhoneInfo phoneInfo) { }
    private record PhoneInfo(@JsonProperty("phoneNumber") String phoneNumber) { }
}
