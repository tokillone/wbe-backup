package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.exception.RedisServiceUnavailableException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;

@Component
public class AuthRateLimiter {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int CAPTCHA_REQUIRED_FAILURES = 3;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_CODE_SENDS = 5;
    private static final Duration CODE_SEND_WINDOW = Duration.ofHours(1);
    private static final Duration CODE_SEND_INTERVAL = Duration.ofSeconds(60);
    private static final String LOGIN_FAILURE_KEY_PREFIX = "wbe:auth:rate:login-failure:";
    private static final String CODE_SEND_KEY_PREFIX = "wbe:auth:rate:email-code:";

    private static final DefaultRedisScript<Long> RECORD_LOGIN_FAILURE_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> ACQUIRE_CODE_SEND_PERMIT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
                return -1
            end
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')
            if count >= tonumber(ARGV[1]) then
                return -2
            end
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[2])
            count = redis.call('INCR', KEYS[2])
            if count == 1 then
                redis.call('PEXPIRE', KEYS[2], ARGV[3])
            end
            return count
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE_CODE_SEND_PERMIT_SCRIPT = new DefaultRedisScript<>("""
            redis.call('DEL', KEYS[1])
            local count = tonumber(redis.call('GET', KEYS[2]) or '0')
            if count <= 1 then
                redis.call('DEL', KEYS[2])
                return 0
            end
            return redis.call('DECR', KEYS[2])
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisOperationExecutor redis;

    public AuthRateLimiter(StringRedisTemplate redisTemplate, RedisOperationExecutor redis) {
        this.redisTemplate = redisTemplate;
        this.redis = redis;
    }

    public void checkLoginAllowed(String account, String ipAddress) {
        if (loginFailureCount(account, ipAddress) >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请15分钟后再试");
        }
    }

    public void recordLoginFailure(String account, String ipAddress) {
        Long result = redis.execute("记录登录失败次数", () ->
                redisTemplate.execute(RECORD_LOGIN_FAILURE_SCRIPT,
                        List.of(loginKey(account, ipAddress)),
                        String.valueOf(LOGIN_FAILURE_WINDOW.toMillis())));
        requireScriptResult("记录登录失败次数", result);
    }

    public void recordLoginSuccess(String account, String ipAddress) {
        redis.execute("清除登录失败次数", () ->
                redisTemplate.delete(loginKey(account, ipAddress)));
    }

    public boolean isLoginLocked(String account, String ipAddress) {
        return loginFailureCount(account, ipAddress) >= MAX_LOGIN_FAILURES;
    }

    public boolean isCaptchaRequired(String account, String ipAddress) {
        return loginFailureCount(account, ipAddress) >= CAPTCHA_REQUIRED_FAILURES;
    }

    public void acquireCodeSendPermit(String email, String purpose) {
        String baseKey = codeSendBaseKey(email, purpose);
        Long result = redis.execute("申请邮件验证码发送限流许可", () ->
                redisTemplate.execute(ACQUIRE_CODE_SEND_PERMIT_SCRIPT,
                        List.of(baseKey + ":cooldown", baseKey + ":count"),
                        String.valueOf(MAX_CODE_SENDS),
                        String.valueOf(CODE_SEND_INTERVAL.toMillis()),
                        String.valueOf(CODE_SEND_WINDOW.toMillis())));
        requireScriptResult("申请邮件验证码发送限流许可", result);
        if (result == -1L) {
            throw new BusinessException(429, "验证码发送过于频繁，请稍后再试");
        }
        if (result == -2L) {
            throw new BusinessException(429, "验证码发送次数过多，请1小时后再试");
        }
    }

    public void releaseCodeSendPermit(String email, String purpose) {
        String baseKey = codeSendBaseKey(email, purpose);
        Long result = redis.execute("释放邮件验证码发送限流许可", () ->
                redisTemplate.execute(RELEASE_CODE_SEND_PERMIT_SCRIPT,
                        List.of(baseKey + ":cooldown", baseKey + ":count")));
        requireScriptResult("释放邮件验证码发送限流许可", result);
    }

    private long loginFailureCount(String account, String ipAddress) {
        String value = redis.execute("读取登录失败次数", () ->
                redisTemplate.opsForValue().get(loginKey(account, ipAddress)));
        if (value == null) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new RedisServiceUnavailableException("读取登录失败次数", ex);
        }
    }

    private String loginKey(String account, String ipAddress) {
        return LOGIN_FAILURE_KEY_PREFIX + digest(normalize(account) + "|" + normalize(ipAddress));
    }

    private String codeSendBaseKey(String email, String purpose) {
        String digest = digest(normalize(purpose) + "|" + normalize(email));
        return CODE_SEND_KEY_PREFIX + "{" + digest + "}";
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private void requireScriptResult(String operation, Long result) {
        if (result == null) {
            throw new RedisServiceUnavailableException(operation,
                    new IllegalStateException("Redis script returned no result"));
        }
    }
}
