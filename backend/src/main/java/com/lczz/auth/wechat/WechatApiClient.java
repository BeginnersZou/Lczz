package com.lczz.auth.wechat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lczz.auth.config.WechatMiniProperties;
import com.lczz.common.exception.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class WechatApiClient implements WechatIdentityGateway {
    private static final String CODE_SESSION_URL = "https://api.weixin.qq.com/sns/jscode2session";
    private static final String TOKEN_URL = "https://api.weixin.qq.com/cgi-bin/token";
    private static final String PHONE_URL = "https://api.weixin.qq.com/wxa/business/getuserphonenumber";

    private final RestClient restClient;
    private final WechatMiniProperties properties;
    private final Clock clock;
    private volatile CachedToken cachedToken;

    public WechatApiClient(RestClient restClient, WechatMiniProperties properties, Clock clock) {
        this.restClient = restClient;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public WechatIdentity exchangeLoginCode(String code) {
        requireConfiguration();
        try {
            CodeSessionResponse response = restClient.get().uri(CODE_SESSION_URL, builder -> builder
                            .queryParam("appid", properties.appId())
                            .queryParam("secret", properties.appSecret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code").build())
                    .retrieve().body(CodeSessionResponse.class);
            if (response == null || response.errorCode() != null && response.errorCode() != 0
                    || response.openId() == null || response.openId().isBlank()) {
                throw wechatFailure(response == null ? null : response.errorMessage());
            }
            return new WechatIdentity(properties.appId(), response.openId(), response.unionId());
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(502, "WECHAT_UNAVAILABLE", "微信服务暂不可用，请稍后重试");
        }
    }

    @Override
    public String exchangePhoneCode(String phoneCode) {
        requireConfiguration();
        try {
            PhoneResponse response = restClient.post().uri(PHONE_URL, builder -> builder
                            .queryParam("access_token", accessToken()).build())
                    .body(Map.of("code", phoneCode)).retrieve().body(PhoneResponse.class);
            if (response == null || response.errorCode() != null && response.errorCode() != 0
                    || response.phoneInfo() == null || response.phoneInfo().phoneNumber() == null) {
                throw wechatFailure(response == null ? null : response.errorMessage());
            }
            return response.phoneInfo().phoneNumber();
        } catch (BusinessException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new BusinessException(502, "WECHAT_UNAVAILABLE", "微信服务暂不可用，请稍后重试");
        }
    }

    private synchronized String accessToken() {
        Instant now = clock.instant();
        if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(60))) {
            return cachedToken.value();
        }
        TokenResponse response = restClient.get().uri(TOKEN_URL, builder -> builder
                        .queryParam("grant_type", "client_credential")
                        .queryParam("appid", properties.appId())
                        .queryParam("secret", properties.appSecret()).build())
                .retrieve().body(TokenResponse.class);
        if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
            throw wechatFailure(response == null ? null : response.errorMessage());
        }
        long seconds = response.expiresIn() == null ? 7200 : response.expiresIn();
        cachedToken = new CachedToken(response.accessToken(), now.plusSeconds(seconds));
        return cachedToken.value();
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
