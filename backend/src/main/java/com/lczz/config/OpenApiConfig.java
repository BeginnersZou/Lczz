package com.lczz.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI lczzOpenApi() {
        return new OpenAPI().info(new Info()
                .title("力创之尊 API")
                .description("后台管理系统与微信小程序统一接口")
                .version("v1"));
    }
}
