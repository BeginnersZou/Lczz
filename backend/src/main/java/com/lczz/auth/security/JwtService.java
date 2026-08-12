package com.lczz.auth.security;

import com.lczz.auth.config.JwtProperties;
import com.lczz.auth.domain.AuthenticatedUser;
import com.lczz.common.exception.BusinessException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtProperties properties;
    private final Clock clock;
    private final SecretKey key;

    public JwtService(JwtProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        if (properties.secret() == null || properties.secret().getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        }
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issue(AuthenticatedUser user) {
        Instant issuedAt = clock.instant();
        Duration duration = Duration.ofMinutes(properties.accessTokenMinutes());
        Instant expiresAt = issuedAt.plus(duration);
        String token = Jwts.builder().issuer(properties.issuer()).subject(Long.toString(user.userId()))
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt)).signWith(key).compact();
        return new IssuedToken(token, duration.toSeconds(), expiresAt);
    }

    public long parseUserId(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build()
                    .parseSignedClaims(token).getPayload();
            return Long.parseLong(claims.getSubject());
        } catch (RuntimeException exception) {
            throw new BusinessException(401, "INVALID_TOKEN", "登录状态已失效");
        }
    }

    public record IssuedToken(String value, long expiresInSeconds, Instant expiresAt) { }
}
