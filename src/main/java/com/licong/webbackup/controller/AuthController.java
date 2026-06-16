package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.LoginRequest;
import com.licong.webbackup.dto.LoginResponse;
import com.licong.webbackup.dto.RegisterRequest;
import com.licong.webbackup.dto.ResetPasswordRequest;
import com.licong.webbackup.dto.SendEmailCodeRequest;
import com.licong.webbackup.dto.UserResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.service.AuthService;
import com.licong.webbackup.service.AuthTokenService;
import com.licong.webbackup.service.VerificationCodeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final VerificationCodeService verificationCodeService;
    private final AuthTokenService authTokenService;

    public AuthController(AuthService authService,
                          VerificationCodeService verificationCodeService,
                          AuthTokenService authTokenService) {
        this.authService = authService;
        this.verificationCodeService = verificationCodeService;
        this.authTokenService = authTokenService;
    }

    @PostMapping("/register/send-code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendEmailCodeRequest request) {
        verificationCodeService.sendRegisterCode(request.getEmail().trim().toLowerCase());
        return ApiResponse.success("注册验证码已发送", null);
    }

    @PostMapping("/register")
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.success("注册成功", authService.register(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String ipAddress = resolveClientIp(servletRequest);
        String userAgent = servletRequest.getHeader("User-Agent");
        return ApiResponse.success("登录成功", authService.login(request, ipAddress, userAgent));
    }

    @PostMapping("/password/forgot/send-code")
    public ApiResponse<Void> sendResetPasswordCode(@Valid @RequestBody SendEmailCodeRequest request) {
        verificationCodeService.sendResetPasswordCode(request.getEmail().trim().toLowerCase());
        return ApiResponse.success("密码找回验证码已发送", null);
    }

    @PostMapping("/password/reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("密码重置成功", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = authTokenService.getUserByToken(resolveBearerToken(authorization));
        return ApiResponse.success(UserResponse.from(user));
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
