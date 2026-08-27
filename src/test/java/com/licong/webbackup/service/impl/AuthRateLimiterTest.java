package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new AuthRateLimiter(redisTemplate, new RedisOperationExecutor());
    }

    @Test
    void requiresCaptchaAfterThreeLoginFailuresAndLocksAfterFive() {
        when(valueOperations.get(anyString())).thenReturn("2", "3", "4", "5", "5");

        assertThat(rateLimiter.isCaptchaRequired("user@example.com", "127.0.0.1")).isFalse();
        assertThat(rateLimiter.isCaptchaRequired("user@example.com", "127.0.0.1")).isTrue();
        assertThat(rateLimiter.isLoginLocked("user@example.com", "127.0.0.1")).isFalse();
        assertThat(rateLimiter.isLoginLocked("user@example.com", "127.0.0.1")).isTrue();
        assertThatThrownBy(() -> rateLimiter.checkLoginAllowed("user@example.com", "127.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void recordsLoginFailureWithAtomicScriptAndClearsOnSuccess() {
        doReturn(1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), eq("900000"));

        assertThatCode(() -> rateLimiter.recordLoginFailure("User@example.com", "127.0.0.1"))
                .doesNotThrowAnyException();
        rateLimiter.recordLoginSuccess("User@example.com", "127.0.0.1");

        verify(redisTemplate).delete(org.mockito.ArgumentMatchers.<String>argThat(key ->
                key != null && key.startsWith("wbe:auth:rate:login-failure:")));
    }

    @Test
    void enforcesEmailCooldownAndHourlyLimitFromAtomicPermitResult() {
        doReturn(-1L, -2L, 3L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(),
                        eq("5"), eq("60000"), eq("3600000"));

        assertThatThrownBy(() -> rateLimiter.acquireCodeSendPermit("a@example.com", "register"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码发送过于频繁，请稍后再试")
                .extracting("code")
                .isEqualTo(429);
        assertThatThrownBy(() -> rateLimiter.acquireCodeSendPermit("a@example.com", "register"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码发送次数过多，请1小时后再试")
                .extracting("code")
                .isEqualTo(429);
        assertThatCode(() -> rateLimiter.acquireCodeSendPermit("a@example.com", "register"))
                .doesNotThrowAnyException();

        verify(redisTemplate, org.mockito.Mockito.times(3))
                .execute(any(RedisScript.class),
                        argThat((List<String> keys) -> keys.size() == 2
                                && keys.get(0).endsWith(":cooldown")
                                && keys.get(1).endsWith(":count")),
                        eq("5"), eq("60000"), eq("3600000"));
    }
}
