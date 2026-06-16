package com.licong.webbackup.service;

public interface VerificationCodeService {

    void sendRegisterCode(String email);

    void sendResetPasswordCode(String email);

    void verifyRegisterCode(String email, String code);

    void verifyResetPasswordCode(String email, String code);
}
