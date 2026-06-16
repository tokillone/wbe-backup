package com.licong.webbackup.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "mail")
public class MailProperties {

    private String host;
    private Integer port;
    private String username;
    private String password;
    private String defaultEncoding = "UTF-8";
    private Map<String, Object> properties = new HashMap<>();
}
