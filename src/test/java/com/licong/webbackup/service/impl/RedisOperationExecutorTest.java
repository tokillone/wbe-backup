package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.GlobalExceptionHandler;
import com.licong.webbackup.exception.RedisServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisOperationExecutorTest {

    @Test
    void failsExplicitlyWhenRedisIsUnavailable() {
        RedisOperationExecutor executor = new RedisOperationExecutor();

        assertThatThrownBy(() -> executor.execute("测试读取", () -> {
            throw new RedisConnectionFailureException("connection refused");
        }))
                .isInstanceOf(RedisServiceUnavailableException.class)
                .hasMessage("Redis operation failed: 测试读取")
                .hasCauseInstanceOf(RedisConnectionFailureException.class);
    }

    @Test
    void mapsRedisOutageToExplicitServiceUnavailableResponse() {
        RedisServiceUnavailableException exception = new RedisServiceUnavailableException(
                "读取登录会话", new RedisConnectionFailureException("connection refused"));

        var response = new GlobalExceptionHandler()
                .handleRedisServiceUnavailableException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(503);
        assertThat(response.getBody().getMessage()).isEqualTo("认证服务暂时不可用，请稍后重试");
    }
}
