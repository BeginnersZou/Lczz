package com.lczz.auth.config;

import java.net.http.HttpClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AuthConfigurationTests {

    @Test
    void wechatHttpClientPrefersHttp11ForGatewayCompatibility() {
        HttpClient httpClient = new AuthConfiguration().wechatHttpClient();

        assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
    }
}
