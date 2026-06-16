package com.licong.webbackup.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LoginLog {

    private Long logId;
    private Long userId;
    private LocalDateTime loginTime;
    private String ipAddress;
    private String userAgent;
}
