package com.licong.webbackup.service;

import com.licong.webbackup.entity.User;

public interface AuthTokenService {

    String createToken(User user);

    User getUserByToken(String token);

    long getExpiresInSeconds();
}
