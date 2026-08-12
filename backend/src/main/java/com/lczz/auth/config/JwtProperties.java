package com.lczz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lczz.jwt")
public record JwtProperties(String issuer, String secret, long accessTokenMinutes) {
}
