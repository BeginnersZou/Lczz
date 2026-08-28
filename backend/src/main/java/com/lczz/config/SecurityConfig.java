package com.lczz.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lczz.auth.security.JwtAuthenticationFilter;
import com.lczz.common.api.ApiResponse;
import com.lczz.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private static final String[] PUBLIC_AUTH = {
            "/api/auth/login", "/api/auth/wechat/login", "/api/auth/wechat/bind-phone",
            "/api/v1/auth/login", "/api/v1/auth/wechat/login", "/api/v1/auth/wechat/bind-phone",
            "/api/auth/dev/login", "/api/v1/auth/dev/login"
    };

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper objectMapper,
                                            JwtAuthenticationFilter jwtFilter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/files/access/**", "/api/v1/files/access/**").permitAll()
                        .requestMatchers(PUBLIC_AUTH).permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, exception) -> {
                            BusinessException failure = request.getAttribute("authFailure") instanceof BusinessException value
                                    ? value : new BusinessException(401, "UNAUTHORIZED", "请先登录");
                            writeFailure(response, objectMapper, 401, failure.getCode(), failure.getMessage(), request.getAttribute("requestId"));
                        })
                        .accessDeniedHandler((request, response, exception) ->
                                writeFailure(response, objectMapper, 403, "FORBIDDEN", "无权访问该资源", request.getAttribute("requestId"))))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeFailure(HttpServletResponse response, ObjectMapper mapper, int status,
                                     String error, String message, Object requestId) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), ApiResponse.failure(
                status, error, message, requestId == null ? "" : requestId.toString()));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
