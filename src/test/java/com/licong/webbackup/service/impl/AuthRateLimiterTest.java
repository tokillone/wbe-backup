package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthRateLimiterTest {

    @Test
    void requiresCaptchaAfterThreeLoginFailuresAndLocksAfterFive() {
        AuthRateLimiter rateLimiter = new AuthRateLimiter();
        String account = "user@example.com";
        String ipAddress = "127.0.0.1";

        rateLimiter.recordLoginFailure(account, ipAddress);
        rateLimiter.recordLoginFailure(account, ipAddress);
        assertThat(rateLimiter.isCaptchaRequired(account, ipAddress)).isFalse();

        rateLimiter.recordLoginFailure(account, ipAddress);
        assertThat(rateLimiter.isCaptchaRequired(account, ipAddress)).isTrue();
        assertThat(rateLimiter.isLoginLocked(account, ipAddress)).isFalse();

        rateLimiter.recordLoginFailure(account, ipAddress);
        rateLimiter.recordLoginFailure(account, ipAddress);

        assertThat(rateLimiter.isLoginLocked(account, ipAddress)).isTrue();
        assertThatThrownBy(() -> rateLimiter.checkLoginAllowed(account, ipAddress))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(429);
    }

    @Test
    void loginSuccessClearsFailureState() {
        AuthRateLimiter rateLimiter = new AuthRateLimiter();
        String account = "user@example.com";
        String ipAddress = "127.0.0.1";

        rateLimiter.recordLoginFailure(account, ipAddress);
        rateLimiter.recordLoginFailure(account, ipAddress);
        rateLimiter.recordLoginFailure(account, ipAddress);
        rateLimiter.recordLoginSuccess(account, ipAddress);

        assertThat(rateLimiter.isCaptchaRequired(account, ipAddress)).isFalse();
        assertThat(rateLimiter.isLoginLocked(account, ipAddress)).isFalse();
    }
}
