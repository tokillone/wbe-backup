package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.CaptchaResponse;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.service.CaptchaService;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CaptchaServiceImpl implements CaptchaService {

    private static final Duration CAPTCHA_TTL = Duration.ofMinutes(5);
    private static final int IMAGE_WIDTH = 120;
    private static final int IMAGE_HEIGHT = 42;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, CaptchaEntry> captchas = new ConcurrentHashMap<>();

    @Override
    public CaptchaResponse createCaptcha() {
        clearExpiredCaptchas();
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String code = String.format("%04d", RANDOM.nextInt(10_000));
        captchas.put(captchaId, new CaptchaEntry(code, LocalDateTime.now().plus(CAPTCHA_TTL)));

        return CaptchaResponse.builder()
                .captchaId(captchaId)
                .imageBase64(drawCaptcha(code))
                .expiresIn(CAPTCHA_TTL.toSeconds())
                .build();
    }

    @Override
    public void verifyCaptcha(String captchaId, String captchaCode) {
        if (captchaId == null || captchaId.isBlank() || captchaCode == null || captchaCode.isBlank()) {
            throw new BusinessException(428, "需要图形验证码，请完成验证后重试");
        }

        CaptchaEntry entry = captchas.remove(captchaId.trim());
        if (entry == null || entry.expiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(428, "图形验证码已过期，请刷新后重试");
        }
        if (!entry.code().equals(captchaCode.trim())) {
            throw new BusinessException(428, "图形验证码不正确，请刷新后重试");
        }
    }

    private String drawCaptcha(String code) {
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(239, 247, 249));
            graphics.fillRoundRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT, 10, 10);

            for (int i = 0; i < 7; i++) {
                graphics.setColor(new Color(54 + RANDOM.nextInt(64), 116 + RANDOM.nextInt(72), 132 + RANDOM.nextInt(72), 130));
                graphics.setStroke(new BasicStroke(1.2f + RANDOM.nextFloat()));
                int x1 = RANDOM.nextInt(IMAGE_WIDTH);
                int y1 = RANDOM.nextInt(IMAGE_HEIGHT);
                int x2 = RANDOM.nextInt(IMAGE_WIDTH);
                int y2 = RANDOM.nextInt(IMAGE_HEIGHT);
                graphics.drawLine(x1, y1, x2, y2);
            }

            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                graphics.setColor(new Color(16 + RANDOM.nextInt(42), 82 + RANDOM.nextInt(42), 104 + RANDOM.nextInt(42)));
                double angle = Math.toRadians(RANDOM.nextInt(25) - 12);
                graphics.rotate(angle, 22 + i * 22, 27);
                graphics.drawString(String.valueOf(code.charAt(i)), 18 + i * 23, 30 + RANDOM.nextInt(4));
                graphics.rotate(-angle, 22 + i * 22, 27);
            }

            for (int i = 0; i < 26; i++) {
                graphics.setColor(new Color(15, 101, 145, 75));
                graphics.fillOval(RANDOM.nextInt(IMAGE_WIDTH), RANDOM.nextInt(IMAGE_HEIGHT), 2, 2);
            }
        } finally {
            graphics.dispose();
        }

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return Base64.getEncoder().encodeToString(output.toByteArray());
        } catch (IOException ex) {
            throw new BusinessException("图形验证码生成失败，请稍后再试");
        }
    }

    private void clearExpiredCaptchas() {
        LocalDateTime now = LocalDateTime.now();
        captchas.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
    }

    private record CaptchaEntry(String code, LocalDateTime expiresAt) {
    }
}
