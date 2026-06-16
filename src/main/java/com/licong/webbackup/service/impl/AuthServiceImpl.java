package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.LoginRequest;
import com.licong.webbackup.dto.LoginResponse;
import com.licong.webbackup.dto.RegisterRequest;
import com.licong.webbackup.dto.ResetPasswordRequest;
import com.licong.webbackup.dto.UserResponse;
import com.licong.webbackup.entity.LoginLog;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.LoginLogMapper;
import com.licong.webbackup.mapper.UserMapper;
import com.licong.webbackup.service.AuthService;
import com.licong.webbackup.service.AuthTokenService;
import com.licong.webbackup.service.VerificationCodeService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final LoginLogMapper loginLogMapper;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final AuthTokenService authTokenService;

    public AuthServiceImpl(UserMapper userMapper,
                           LoginLogMapper loginLogMapper,
                           PasswordEncoder passwordEncoder,
                           VerificationCodeService verificationCodeService,
                           AuthTokenService authTokenService) {
        this.userMapper = userMapper;
        this.loginLogMapper = loginLogMapper;
        this.passwordEncoder = passwordEncoder;
        this.verificationCodeService = verificationCodeService;
        this.authTokenService = authTokenService;
    }

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String username = request.getUsername().trim();
        String email = request.getEmail().trim().toLowerCase();
        if (userMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException("邮箱已注册");
        }
        verificationCodeService.verifyRegisterCode(email, request.getCode().trim());

        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setFullName(normalizeNullable(request.getFullName()));
        user.setRole("viewer");
        user.setIsActive(true);
        userMapper.insert(user);
        return UserResponse.from(user);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userMapper.findByUsernameOrEmail(request.getAccount().trim());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        if (!Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException("账号已被禁用");
        }

        userMapper.updateLastLogin(user.getUserId());
        LoginLog loginLog = new LoginLog();
        loginLog.setUserId(user.getUserId());
        loginLog.setIpAddress(ipAddress);
        loginLog.setUserAgent(userAgent);
        loginLogMapper.insert(loginLog);

        User latestUser = userMapper.findById(user.getUserId());
        String token = authTokenService.createToken(latestUser);
        return LoginResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .expiresIn(authTokenService.getExpiresInSeconds())
                .user(UserResponse.from(latestUser))
                .build();
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userMapper.findByEmail(email);
        if (user == null) {
            throw new BusinessException("该邮箱未注册");
        }
        verificationCodeService.verifyResetPasswordCode(email, request.getCode().trim());
        userMapper.updatePassword(user.getUserId(), passwordEncoder.encode(request.getNewPassword()));
    }

    private String normalizeNullable(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
