package com.licong.webbackup.service.impl;

import com.licong.webbackup.config.MailProperties;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import com.licong.webbackup.service.VerificationCodeService;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, VerificationCode> codes = new ConcurrentHashMap<>();
    private final JavaMailSender mailSender;
    private final MailProperties mailProperties;
    private final UserMapper userMapper;
    private final AuthRateLimiter authRateLimiter;

    public VerificationCodeServiceImpl(JavaMailSender mailSender,
                                       MailProperties mailProperties,
                                       UserMapper userMapper,
                                       AuthRateLimiter authRateLimiter) {
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
        this.userMapper = userMapper;
        this.authRateLimiter = authRateLimiter;
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
        clearExpiredCodes();
        authRateLimiter.checkCodeSendAllowed(email, purpose.name());
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        codes.put(cacheKey(email, purpose), new VerificationCode(code, LocalDateTime.now().plus(CODE_TTL), 0));

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getUsername());
        message.setTo(email);
        message.setSubject(subject);
        message.setText("您的验证码是：" + code + "，5分钟内有效。若非本人操作，请忽略本邮件。");
        try {
            mailSender.send(message);
            authRateLimiter.recordCodeSent(email, purpose.name());
        } catch (MailException ex) {
            codes.remove(cacheKey(email, purpose));
            throw new BusinessException("验证码邮件发送失败，请检查邮箱配置或稍后重试");
        }
    }

    private void verifyCode(String email, String code, CodePurpose purpose) {
        String key = cacheKey(email, purpose);
        VerificationCode cachedCode = codes.get(key);
        if (cachedCode == null || cachedCode.expiresAt().isBefore(LocalDateTime.now())) {
            codes.remove(key);
            throw new BusinessException("验证码不存在或已过期");
        }
        if (!cachedCode.code().equals(code)) {
            int attempts = cachedCode.attempts() + 1;
            if (attempts >= 5) {
                codes.remove(key);
                throw new BusinessException(429, "验证码错误次数过多，请重新获取");
            }
            codes.put(key, new VerificationCode(cachedCode.code(), cachedCode.expiresAt(), attempts));
            throw new BusinessException("验证码不正确");
        }
        codes.remove(key);
    }

    private void clearExpiredCodes() {
        LocalDateTime now = LocalDateTime.now();
        codes.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private String cacheKey(String email, CodePurpose purpose) {
        return purpose.name() + ":" + email.toLowerCase();
    }

    private enum CodePurpose {
        REGISTER,
        RESET_PASSWORD
    }

    private record VerificationCode(String code, LocalDateTime expiresAt, int attempts) {
    }
}
