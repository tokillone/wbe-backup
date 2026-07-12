package com.licong.webbackup.dto;

import com.licong.webbackup.entity.User;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class UserResponse {

    private Long userId;
    private String username;
    private String email;
    private String fullName;
    private String role;
    private Boolean canUpload;
    private Boolean canReviewUploads;
    private Boolean canSyncData;
    private Boolean canDownload;
    private Boolean isActive;
    private LocalDateTime lastLogin;

    public static UserResponse from(User user) {
        boolean isAdmin = "admin".equals(user.getRole());
        boolean defaultManager = isAdmin || "editor".equals(user.getRole());
        return UserResponse.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole())
                .canUpload(isAdmin || (user.getCanUpload() == null ? defaultManager : Boolean.TRUE.equals(user.getCanUpload())))
                .canReviewUploads(isAdmin || (user.getCanReviewUploads() == null ? defaultManager : Boolean.TRUE.equals(user.getCanReviewUploads())))
                .canSyncData(isAdmin || (user.getCanSyncData() == null ? defaultManager : Boolean.TRUE.equals(user.getCanSyncData())))
                .canDownload(isAdmin || user.getCanDownload() == null || Boolean.TRUE.equals(user.getCanDownload()))
                .isActive(user.getIsActive())
                .lastLogin(user.getLastLogin())
                .build();
    }
}
