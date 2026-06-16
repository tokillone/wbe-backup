package com.licong.webbackup.service;

import com.licong.webbackup.dto.LoginRequest;
import com.licong.webbackup.dto.LoginResponse;
import com.licong.webbackup.dto.RegisterRequest;
import com.licong.webbackup.dto.ResetPasswordRequest;
import com.licong.webbackup.dto.UserResponse;

public interface AuthService {

    UserResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request, String ipAddress, String userAgent);

    void resetPassword(ResetPasswordRequest request);
}
