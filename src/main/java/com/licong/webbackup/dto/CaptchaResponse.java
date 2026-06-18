package com.licong.webbackup.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CaptchaResponse {

    private String captchaId;
    private String imageBase64;
    private Long expiresIn;
}
