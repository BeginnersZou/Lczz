package com.lczz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lczz.dev-auth")
public record DevelopmentAuthProperties(boolean enabled, String password) {
    public void validate() {
        if (!enabled) return;
        if (password == null || password.length() < 12) {
            throw new IllegalArgumentException("DEV_AUTH_PASSWORD must contain at least 12 characters");
        }
    }
}
