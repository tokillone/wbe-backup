package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.CaptchaResponse;
import com.licong.webbackup.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CaptchaServiceImpl captchaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        captchaService = new CaptchaServiceImpl(redisTemplate, new RedisOperationExecutor());
    }

    @Test
    void createsBase64CaptchaWithFiveMinuteRedisExpiry() {
        CaptchaResponse response = captchaService.createCaptcha();

        assertThat(response.getCaptchaId()).hasSize(32);
        assertThat(response.getExpiresIn()).isEqualTo(300);
        assertThat(Base64.getDecoder().decode(response.getImageBase64())).isNotEmpty();
        verify(valueOperations).set(
                eq("wbe:auth:captcha:" + response.getCaptchaId()),
                anyString(),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void verifiesCaptchaOnceAndConsumesItAtomically() {
        doReturn("1234").doReturn(null).when(redisTemplate)
                .execute(any(RedisScript.class), anyList());

        assertThatCode(() -> captchaService.verifyCaptcha("captcha-id", "1234"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> captchaService.verifyCaptcha("captcha-id", "1234"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图形验证码已过期，请刷新后重试")
                .extracting("code")
                .isEqualTo(428);
    }

    @Test
    void rejectsWrongCaptchaAfterConsumingIt() {
        doReturn("1234").when(redisTemplate)
                .execute(any(RedisScript.class), anyList());

        assertThatThrownBy(() -> captchaService.verifyCaptcha("captcha-id", "0000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("图形验证码不正确，请刷新后重试")
                .extracting("code")
                .isEqualTo(428);
    }
}
