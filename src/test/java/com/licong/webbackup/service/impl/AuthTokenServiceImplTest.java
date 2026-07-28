package com.licong.webbackup.service.impl;

import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthTokenServiceImplTest {

    private UserMapper userMapper;
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private AuthTokenServiceImpl tokenService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userMapper = mock(UserMapper.class);
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenService = new AuthTokenServiceImpl(
                userMapper, redisTemplate, new RedisOperationExecutor());
    }

    @Test
    void createsBearerTokenWithTwelveHourRedisExpiry() {
        User user = activeUser(42L);

        String token = tokenService.createToken(user);

        assertThat(token).hasSize(32);
        assertThat(tokenService.getExpiresInSeconds()).isEqualTo(43_200);
        verify(valueOperations).set(
                eq("wbe:auth:session:" + token),
                eq("42"),
                eq(Duration.ofHours(12)));
    }

    @Test
    void treatsExpiredRedisSessionAsInvalidToken() {
        when(valueOperations.get("wbe:auth:session:expired-token")).thenReturn(null);

        assertThatThrownBy(() -> tokenService.getUserByToken("expired-token"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("未登录或登录状态已失效")
                .extracting("code")
                .isEqualTo(401);
    }

    @Test
    void logoutDeletesSessionAndSubsequentAuthenticationFails() {
        when(valueOperations.get(anyString())).thenReturn(null);

        tokenService.revokeToken("logout-token");

        verify(redisTemplate).delete("wbe:auth:session:logout-token");
        assertThatThrownBy(() -> tokenService.getUserByToken("logout-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(401);
    }

    private User activeUser(Long userId) {
        User user = new User();
        user.setUserId(userId);
        user.setIsActive(true);
        return user;
    }
}
