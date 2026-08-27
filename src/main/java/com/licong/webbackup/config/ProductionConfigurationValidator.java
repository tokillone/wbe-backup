package com.licong.webbackup.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfigurationValidator {

    public ProductionConfigurationValidator(Environment environment) {
        requireNonBlank(environment, "spring.datasource.url");
        requireNonBlank(environment, "spring.datasource.username");
        requireNonBlank(environment, "spring.datasource.password");
        requireNonBlank(environment, "mail.host");
        requireNonBlank(environment, "mail.username");
        requireNonBlank(environment, "mail.password");
        requireNonBlank(environment, "wbe.storage.upload-dir");
        requireNonBlank(environment, "server.tomcat.remoteip.internal-proxies");
    }

    private void requireNonBlank(Environment environment, String propertyName) {
        String value = environment.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("生产环境配置 " + propertyName + " 不能为空");
        }
    }
}
