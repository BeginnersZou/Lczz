package com.lczz.auth.service;

import com.lczz.auth.wechat.WechatIdentity;
import com.lczz.common.exception.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoginChallengeStore {
    private static final Duration TIME_TO_LIVE = Duration.ofMinutes(5);
    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public LoginChallengeStore(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Transactional
    public void put(String loginCode, WechatIdentity identity) {
        String codeHash = key(loginCode);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbcTemplate.update("DELETE FROM wechat_login_challenge WHERE code_hash=?", codeHash);
        jdbcTemplate.update("INSERT INTO wechat_login_challenge(code_hash, app_id, open_id, union_id, expires_at, created_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                codeHash, identity.appId(), identity.openId(), identity.unionId(), now.plus(TIME_TO_LIVE), now);
        jdbcTemplate.update("DELETE FROM wechat_login_challenge WHERE expires_at<?", now);
    }

    @Transactional
    public WechatIdentity consume(String loginCode) {
        String codeHash = key(loginCode);
        List<Challenge> rows = jdbcTemplate.query(
                "SELECT app_id, open_id, union_id, expires_at FROM wechat_login_challenge "
                        + "WHERE code_hash=? FOR UPDATE",
                (rs, rowNum) -> new Challenge(
                        new WechatIdentity(rs.getString("app_id"), rs.getString("open_id"), rs.getString("union_id")),
                        rs.getTimestamp("expires_at").toLocalDateTime()),
                codeHash);
        jdbcTemplate.update("DELETE FROM wechat_login_challenge WHERE code_hash=?", codeHash);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (rows.isEmpty() || rows.getFirst().expiresAt().isBefore(now)) {
            throw new BusinessException(400, "WECHAT_LOGIN_EXPIRED", "微信登录状态已过期，请重新登录");
        }
        return rows.getFirst().identity();
    }

    private String key(String loginCode) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(loginCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record Challenge(WechatIdentity identity, LocalDateTime expiresAt) { }
}
