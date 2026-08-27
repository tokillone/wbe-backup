package com.licong.webbackup.service.impl;

import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import com.licong.webbackup.service.AuthTokenService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
public class AuthTokenServiceImpl implements AuthTokenService {

    private static final Duration TOKEN_TTL = Duration.ofHours(12);
    private static final String SESSION_KEY_PREFIX = "wbe:auth:session:";

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final RedisOperationExecutor redis;

    public AuthTokenServiceImpl(UserMapper userMapper,
                                StringRedisTemplate redisTemplate,
                                RedisOperationExecutor redis) {
        this.userMapper = userMapper;
        this.redisTemplate = redisTemplate;
        this.redis = redis;
    }

    @Override
    public String createToken(User user) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.execute("创建登录会话", () ->
                redisTemplate.opsForValue().set(sessionKey(token), user.getUserId().toString(), TOKEN_TTL));
        return token;
    }

    @Override
    public User getUserByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        String normalizedToken = token.trim();
        String userIdValue = redis.execute("读取登录会话",
                () -> redisTemplate.opsForValue().get(sessionKey(normalizedToken)));
        if (userIdValue == null) {
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        Long userId;
        try {
            userId = Long.valueOf(userIdValue);
        } catch (NumberFormatException ex) {
            redis.execute("删除无效登录会话", () -> redisTemplate.delete(sessionKey(normalizedToken)));
            throw new BusinessException(401, "未登录或登录状态已失效");
        }
        User user = userMapper.findById(userId);
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
        redis.execute("退出登录", () -> redisTemplate.delete(sessionKey(token.trim())));
    }

    @Override
    public long getExpiresInSeconds() {
        return TOKEN_TTL.toSeconds();
    }

    private String sessionKey(String token) {
        return SESSION_KEY_PREFIX + token;
    }
}
