package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.BusinessException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AuthRateLimiter {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int CAPTCHA_REQUIRED_FAILURES = 3;
    private static final Duration LOGIN_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final int MAX_CODE_SENDS = 5;
    private static final Duration CODE_SEND_WINDOW = Duration.ofHours(1);
    private static final Duration CODE_SEND_INTERVAL = Duration.ofSeconds(60);

    private final Map<String, LoginFailureBucket> loginFailures = new ConcurrentHashMap<>();
    private final Map<String, CodeSendBucket> codeSends = new ConcurrentHashMap<>();

    public void checkLoginAllowed(String account, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        String key = loginKey(account, ipAddress);
        LoginFailureBucket bucket = loginFailures.get(key);
        if (bucket == null) {
            return;
        }
        if (bucket.resetAt().isBefore(now)) {
            loginFailures.remove(key);
            return;
        }
        if (bucket.count() >= MAX_LOGIN_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请15分钟后再试");
        }
    }

    public void recordLoginFailure(String account, String ipAddress) {
        LocalDateTime now = LocalDateTime.now();
        loginFailures.compute(loginKey(account, ipAddress), (key, bucket) -> {
            if (bucket == null || bucket.resetAt().isBefore(now)) {
                return new LoginFailureBucket(1, now.plus(LOGIN_FAILURE_WINDOW));
            }
            return new LoginFailureBucket(bucket.count() + 1, bucket.resetAt());
        });
    }

    public void recordLoginSuccess(String account, String ipAddress) {
        loginFailures.remove(loginKey(account, ipAddress));
    }

    public boolean isLoginLocked(String account, String ipAddress) {
        LoginFailureBucket bucket = currentLoginFailureBucket(account, ipAddress);
        return bucket != null && bucket.count() >= MAX_LOGIN_FAILURES;
    }

    public boolean isCaptchaRequired(String account, String ipAddress) {
        LoginFailureBucket bucket = currentLoginFailureBucket(account, ipAddress);
        return bucket != null && bucket.count() >= CAPTCHA_REQUIRED_FAILURES;
    }

    public void checkCodeSendAllowed(String email, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        String key = codeSendKey(email, purpose);
        CodeSendBucket bucket = codeSends.get(key);
        if (bucket == null) {
            return;
        }
        if (bucket.windowResetAt().isBefore(now)) {
            codeSends.remove(key);
            return;
        }
        if (bucket.lastSentAt().plus(CODE_SEND_INTERVAL).isAfter(now)) {
            throw new BusinessException(429, "验证码发送过于频繁，请稍后再试");
        }
        if (bucket.count() >= MAX_CODE_SENDS) {
            throw new BusinessException(429, "验证码发送次数过多，请1小时后再试");
        }
    }

    public void recordCodeSent(String email, String purpose) {
        LocalDateTime now = LocalDateTime.now();
        codeSends.compute(codeSendKey(email, purpose), (key, bucket) -> {
            if (bucket == null || bucket.windowResetAt().isBefore(now)) {
                return new CodeSendBucket(1, now, now.plus(CODE_SEND_WINDOW));
            }
            return new CodeSendBucket(bucket.count() + 1, now, bucket.windowResetAt());
        });
    }

    private String loginKey(String account, String ipAddress) {
        return normalize(account) + "|" + normalize(ipAddress);
    }

    private LoginFailureBucket currentLoginFailureBucket(String account, String ipAddress) {
        String key = loginKey(account, ipAddress);
        LoginFailureBucket bucket = loginFailures.get(key);
        if (bucket == null) {
            return null;
        }
        if (bucket.resetAt().isBefore(LocalDateTime.now())) {
            loginFailures.remove(key);
            return null;
        }
        return bucket;
    }

    private String codeSendKey(String email, String purpose) {
        return normalize(purpose) + "|" + normalize(email);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record LoginFailureBucket(int count, LocalDateTime resetAt) {
    }

    private record CodeSendBucket(int count, LocalDateTime lastSentAt, LocalDateTime windowResetAt) {
    }
}
