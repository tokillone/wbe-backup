package com.licong.webbackup.common;

import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.service.AuthTokenService;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class SecuritySupport {

    private final AuthTokenService authTokenService;

    public SecuritySupport(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    public User requireUser(String authorization) {
        return authTokenService.getUserByToken(resolveBearerToken(authorization));
    }

    public User requireAnyRole(String authorization, Set<String> roles) {
        User user = requireUser(authorization);
        if (user.getRole() == null || !roles.contains(user.getRole())) {
            throw new BusinessException(403, "当前账号无权执行该操作");
        }
        return user;
    }

    public User requireAdmin(String authorization) {
        return requireAnyRole(authorization, Set.of("admin"));
    }

    private String resolveBearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        if (authorization.startsWith("Bearer ")) {
            return authorization.substring(7).trim();
        }
        return authorization.trim();
    }
}
