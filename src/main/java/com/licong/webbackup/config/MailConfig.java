package com.licong.webbackup.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Properties;

@Configuration
public class MailConfig {

    @Bean
    public JavaMailSender javaMailSender(MailProperties mailProperties) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        sender.setPort(mailProperties.getPort());
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setDefaultEncoding(Charset.forName(mailProperties.getDefaultEncoding()).name());
        sender.setJavaMailProperties(flattenProperties(mailProperties.getProperties()));
        return sender;
    }

    private Properties flattenProperties(Map<String, Object> source) {
        Properties properties = new Properties();
        flatten("", source, properties);
        return properties;
    }

    @SuppressWarnings("unchecked")
    private void flatten(String prefix, Map<String, Object> source, Properties target) {
        source.forEach((key, value) -> {
            String propertyKey = prefix.isBlank() ? key : prefix + "." + key;
            if (value instanceof Map<?, ?> nested) {
                flatten(propertyKey, (Map<String, Object>) nested, target);
            } else {
                target.put(propertyKey, String.valueOf(value));
            }
        });
    }
}
