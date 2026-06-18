package com.licong.webbackup.service.impl;

import com.licong.webbackup.dto.CaptchaResponse;
import com.licong.webbackup.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CaptchaServiceImplTest {

    @Test
    void createsBase64CaptchaWithFiveMinuteExpiry() {
        CaptchaServiceImpl captchaService = new CaptchaServiceImpl();

        CaptchaResponse response = captchaService.createCaptcha();

        assertThat(response.getCaptchaId()).hasSize(32);
        assertThat(response.getExpiresIn()).isEqualTo(300);
        assertThat(Base64.getDecoder().decode(response.getImageBase64())).isNotEmpty();
    }

    @Test
    void verifiesCaptchaOnceAndConsumesIt() {
        CaptchaServiceImpl captchaService = new CaptchaServiceImpl();
        CaptchaResponse response = captchaService.createCaptcha();
        String code = storedCode(captchaService, response.getCaptchaId());

        assertThatCode(() -> captchaService.verifyCaptcha(response.getCaptchaId(), code))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> captchaService.verifyCaptcha(response.getCaptchaId(), code))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(428);
    }

    @Test
    void rejectsWrongCaptchaAndRequiresRefresh() {
        CaptchaServiceImpl captchaService = new CaptchaServiceImpl();
        CaptchaResponse response = captchaService.createCaptcha();

        assertThatThrownBy(() -> captchaService.verifyCaptcha(response.getCaptchaId(), "00000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(428);
        assertThatThrownBy(() -> captchaService.verifyCaptcha(response.getCaptchaId(), "00000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(428);
    }

    private String storedCode(CaptchaServiceImpl captchaService, String captchaId) {
        Map<?, ?> captchas = (Map<?, ?>) ReflectionTestUtils.getField(captchaService, "captchas");
        Object entry = captchas == null ? null : captchas.get(captchaId);
        assertThat(entry).isNotNull();
        return ReflectionTestUtils.invokeMethod(entry, "code");
    }
}
