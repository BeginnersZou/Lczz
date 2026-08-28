package com.lczz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lczz.wechat.mini")
public record WechatMiniProperties(String appId, String appSecret) {
    public boolean configured() {
        return appId != null && !appId.isBlank() && appSecret != null && !appSecret.isBlank();
    }
}
