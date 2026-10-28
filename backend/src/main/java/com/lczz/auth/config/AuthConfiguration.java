package com.lczz.auth.config;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, WechatMiniProperties.class, AdminBootstrapProperties.class,
        DevelopmentAuthProperties.class})
public class AuthConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Profile("!test")
    RestClient restClient(RestClient.Builder builder) {
        HttpClient httpClient = wechatHttpClient();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        return builder.requestFactory(requestFactory).build();
    }

    @Bean
    @Profile("test")
    RestClient testRestClient(RestClient.Builder builder) {
        // The test profile never calls WeChat; avoid JDK HttpClient's loopback selector on sandboxed hosts.
        return builder.requestFactory(new SimpleClientHttpRequestFactory()).build();
    }

    HttpClient wechatHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }
}
