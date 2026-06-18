package com.licong.webbackup.service;

import com.licong.webbackup.entity.User;

public interface AuthTokenService {

    String createToken(User user);

    User getUserByToken(String token);

    void revokeToken(String token);

    long getExpiresInSeconds();
}
