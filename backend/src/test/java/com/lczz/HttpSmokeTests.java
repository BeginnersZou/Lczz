package com.lczz;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class HttpSmokeTests {
    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void applicationStartsAndExposesExpectedHttpBoundaries() {
        ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
        assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(health.getHeaders().getFirst("X-Request-Id")).isNotBlank();
        assertThat(health.getBody()).contains("UP");

        ResponseEntity<String> openApi = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(openApi.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(openApi.getBody()).contains("力创之尊 API");

        ResponseEntity<String> protectedApi = restTemplate.getForEntity("/api/v1/orders", String.class);
        assertThat(protectedApi.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(protectedApi.getBody()).contains("UNAUTHORIZED");
    }
}
