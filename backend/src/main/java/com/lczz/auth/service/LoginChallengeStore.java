package com.lczz.auth.service;

import com.lczz.auth.wechat.WechatIdentity;
import com.lczz.common.exception.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class LoginChallengeStore {
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(5);
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();
    private final Clock clock;

    public LoginChallengeStore(Clock clock) {
        this.clock = clock;
    }

    public void put(String loginCode, WechatIdentity identity) {
        challenges.put(key(loginCode), new Challenge(identity, clock.instant().plus(TIME_TO_LIVE)));
        if (challenges.size() > 10_000) {
            Instant now = clock.instant();
            challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        }
    }

    public WechatIdentity consume(String loginCode) {
        Challenge challenge = challenges.remove(key(loginCode));
        if (challenge == null || challenge.expiresAt().isBefore(clock.instant())) {
            throw new BusinessException(400, "WECHAT_LOGIN_EXPIRED", "微信登录状态已过期，请重新登录");
        }
        return challenge.identity();
    }

    private String key(String loginCode) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(loginCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Challenge(WechatIdentity identity, Instant expiresAt) { }
}
