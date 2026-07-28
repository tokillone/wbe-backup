package com.licong.webbackup.service.impl;

import com.licong.webbackup.config.MailProperties;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

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

class VerificationCodeServiceImplTest {

    private JavaMailSender mailSender;
    private UserMapper userMapper;
    private AuthRateLimiter rateLimiter;
    private StringRedisTemplate redisTemplate;
    private VerificationCodeServiceImpl codeService;

    @BeforeEach
    void setUp() {
        mailSender = mock(JavaMailSender.class);
        userMapper = mock(UserMapper.class);
        rateLimiter = mock(AuthRateLimiter.class);
        redisTemplate = mock(StringRedisTemplate.class);
        MailProperties mailProperties = new MailProperties();
        mailProperties.setUsername("noreply@example.com");
        codeService = new VerificationCodeServiceImpl(
                mailSender,
                mailProperties,
                userMapper,
                rateLimiter,
                redisTemplate,
                new RedisOperationExecutor());
    }

    @Test
    void storesRegisterCodeSeparatelyWithFiveMinuteExpiry() {
        when(userMapper.findByEmail("a@example.com")).thenReturn(null);
        doReturn(1L).when(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of("wbe:auth:email-code:register:a@example.com")),
                anyString(),
                eq("300000"));

        codeService.sendRegisterCode("a@example.com");

        verify(rateLimiter).acquireCodeSendPermit("a@example.com", "register");
        verify(mailSender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void treatsMissingRedisCodeAsExpired() {
        doReturn(-1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), eq("123456"));

        assertThatThrownBy(() -> codeService.verifyRegisterCode("a@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码不存在或已过期")
                .extracting("code")
                .isEqualTo(400);
    }

    @Test
    void rejectsFirstFourErrorsAndDeletesCodeOnFifthError() {
        doReturn(0L, 0L, 0L, 0L, -2L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), eq("000000"));

        for (int attempt = 1; attempt <= 4; attempt++) {
            assertThatThrownBy(() ->
                    codeService.verifyResetPasswordCode("a@example.com", "000000"))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("验证码不正确")
                    .extracting("code")
                    .isEqualTo(400);
        }
        assertThatThrownBy(() ->
                codeService.verifyResetPasswordCode("a@example.com", "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码错误次数过多，请重新获取")
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void successfulVerificationConsumesCodeImmediately() {
        doReturn(1L, -1L).when(redisTemplate)
                .execute(any(RedisScript.class), anyList(), eq("123456"));

        assertThatCode(() -> codeService.verifyRegisterCode("a@example.com", "123456"))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> codeService.verifyRegisterCode("a@example.com", "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("验证码不存在或已过期");
    }
}
