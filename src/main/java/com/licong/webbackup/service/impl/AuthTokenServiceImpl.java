package com.licong.webbackup.service.impl;

import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import com.licong.webbackup.service.AuthTokenService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);

    private final Map<String, TokenSession> sessions = new ConcurrentHashMap<>();
    private final UserMapper userMapper;

    public AuthTokenServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public String createToken(User user) {
        clearExpiredSessions();
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, new TokenSession(user.getUserId(), LocalDateTime.now().plus(TOKEN_TTL)));
        return token;
    }

    @Override
    public User getUserByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        TokenSession session = sessions.get(token);
        if (session == null || session.expiresAt().isBefore(LocalDateTime.now())) {
            sessions.remove(token);
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        User user = userMapper.findById(session.userId());
        if (user == null || !Boolean.TRUE.equals(user.getIsActive())) {
            throw new BusinessException(401, "用户不存在或已被禁用");
        }
        return user;
    }

    @Override
    public void revokeToken(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        sessions.remove(token);
    }

    @Override
    public long getExpiresInSeconds() {
        return TOKEN_TTL.toSeconds();
    }

    private void clearExpiredSessions() {
        LocalDateTime now = LocalDateTime.now();
        sessions.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record TokenSession(Long userId, LocalDateTime expiresAt) {
    }
}
