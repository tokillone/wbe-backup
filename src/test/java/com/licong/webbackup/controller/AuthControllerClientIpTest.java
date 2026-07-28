package com.licong.webbackup.controller;

import com.licong.webbackup.dto.LoginRequest;
import com.licong.webbackup.service.AuthService;
import com.licong.webbackup.service.AuthTokenService;
import com.licong.webbackup.service.CaptchaService;
import com.licong.webbackup.service.VerificationCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuthControllerClientIpTest {

    @Test
    void ignoresForwardingHeadersThatWereNotAcceptedByTheServletContainer() {
        AuthService authService = mock(AuthService.class);
        AuthController controller = new AuthController(
                authService,
                mock(VerificationCodeService.class),
                mock(AuthTokenService.class),
                mock(CaptchaService.class)
        );
        LoginRequest loginRequest = new LoginRequest();
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setRemoteAddr("203.0.113.10");
        servletRequest.addHeader("X-Forwarded-For", "198.51.100.25");
        servletRequest.addHeader("X-Real-IP", "198.51.100.26");
        servletRequest.addHeader("User-Agent", "test-agent");

        controller.login(loginRequest, servletRequest);

        verify(authService).login(eq(loginRequest), eq("203.0.113.10"), eq("test-agent"));
    }
}
