package com.licong.webbackup.service.impl;

import com.licong.webbackup.config.MailProperties;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.exception.RedisServiceUnavailableException;
import com.licong.webbackup.mapper.UserMapper;
import com.licong.webbackup.service.VerificationCodeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final String CODE_KEY_PREFIX = "wbe:auth:email-code:";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DefaultRedisScript<Long> STORE_CODE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'code', ARGV[1], 'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[2])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> VERIFY_CODE_SCRIPT = new DefaultRedisScript<>("""
            local stored = redis.call('HGET', KEYS[1], 'code')
            if not stored then
                return -1
            end
            if stored == ARGV[1] then
                redis.call('DEL', KEYS[1])
                return 1
            end
            local attempts = redis.call('HINCRBY', KEYS[1], 'attempts', 1)
            if attempts >= 5 then
                redis.call('DEL', KEYS[1])
                return -2
            end
            return 0
            """, Long.class);

    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final UserMapper userMapper;
    private final AuthRateLimiter authRateLimiter;
    private final StringRedisTemplate redisTemplate;
    private final RedisOperationExecutor redis;

    public VerificationCodeServiceImpl(JavaMailSender mailSender,
                                       MailProperties mailProperties,
                                       UserMapper userMapper,
                                       AuthRateLimiter authRateLimiter,
                                       StringRedisTemplate redisTemplate,
                                       RedisOperationExecutor redis) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.userMapper = userMapper;
        this.authRateLimiter = authRateLimiter;
        this.redisTemplate = redisTemplate;
        this.redis = redis;
    }

    @Override
    public void sendRegisterCode(String email) {
        if (userMapper.findByEmail(email) != null) {
            throw new BusinessException("该邮箱已注册");
        }
        sendCode(email, CodePurpose.REGISTER, "污水管理系统注册验证码");
    }

    @Override
    public void sendResetPasswordCode(String email) {
        if (userMapper.findByEmail(email) == null) {
            throw new BusinessException("该邮箱未注册");
        }
        sendCode(email, CodePurpose.RESET_PASSWORD, "污水管理系统密码找回验证码");
    }

    @Override
    public void verifyRegisterCode(String email, String code) {
        verifyCode(email, code, CodePurpose.REGISTER);
    }

    @Override
    public void verifyResetPasswordCode(String email, String code) {
        verifyCode(email, code, CodePurpose.RESET_PASSWORD);
    }

    private void sendCode(String email, CodePurpose purpose, String subject) {
        authRateLimiter.acquireCodeSendPermit(email, purpose.keyPart());
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String key = cacheKey(email, purpose);
        Long stored = redis.execute("保存邮件验证码", () ->
                redisTemplate.execute(STORE_CODE_SCRIPT, List.of(key),
                        code, String.valueOf(CODE_TTL.toMillis())));
        if (!Long.valueOf(1L).equals(stored)) {
            throw new RedisServiceUnavailableException("保存邮件验证码",
                    new IllegalStateException("Redis did not confirm the write"));
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getUsername());
        message.setTo(email);
        message.setSubject(subject);
        message.setText("您的验证码是：" + code + "，5分钟内有效。若非本人操作，请忽略本邮件。");
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            redis.execute("删除发送失败的邮件验证码", () -> redisTemplate.delete(key));
            authRateLimiter.releaseCodeSendPermit(email, purpose.keyPart());
            throw new BusinessException("验证码邮件发送失败，请检查邮箱配置或稍后重试");
        }
    }

    private void verifyCode(String email, String code, CodePurpose purpose) {
        String key = cacheKey(email, purpose);
        Long result = redis.execute("验证邮件验证码", () ->
                redisTemplate.execute(VERIFY_CODE_SCRIPT, List.of(key), code));
        if (result == null) {
            throw new RedisServiceUnavailableException("验证邮件验证码",
                    new IllegalStateException("Redis script returned no result"));
        }
        if (result == -1L) {
            throw new BusinessException("验证码不存在或已过期");
        }
        if (result == -2L) {
            throw new BusinessException(429, "验证码错误次数过多，请重新获取");
        }
        if (result == 0L) {
            throw new BusinessException("验证码不正确");
        }
    }

    private String cacheKey(String email, CodePurpose purpose) {
        return CODE_KEY_PREFIX + purpose.keyPart() + ":" + email.trim().toLowerCase();
    }

    private enum CodePurpose {
        REGISTER("register"),
        RESET_PASSWORD("reset-password");

        private final String keyPart;

        CodePurpose(String keyPart) {
            this.keyPart = keyPart;
        }

        private String keyPart() {
            return keyPart;
        }
    }
}
