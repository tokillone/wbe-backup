package com.licong.webbackup.service;

import com.licong.webbackup.dto.CaptchaResponse;

public interface CaptchaService {

    CaptchaResponse createCaptcha();

    void verifyCaptcha(String captchaId, String captchaCode);
}
