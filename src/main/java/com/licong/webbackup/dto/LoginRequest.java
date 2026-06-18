package com.licong.webbackup.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(max = 100, message = "账号最多100位")
    private String account;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码最多64位")
    private String password;

    @Size(max = 64, message = "图形验证码编号不合法")
    private String captchaId;

    @Size(max = 8, message = "图形验证码最多8位")
    private String captchaCode;
}
