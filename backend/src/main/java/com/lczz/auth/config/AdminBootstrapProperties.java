package com.lczz.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lczz.bootstrap-admin")
public record AdminBootstrapProperties(String username, String password, String nickname) {
    public boolean enabled() {
        return username != null && !username.isBlank() && password != null && !password.isBlank();
    }
}
