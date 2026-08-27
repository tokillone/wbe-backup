package com.licong.webbackup.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@Component
@ConfigurationProperties(prefix = "wbe.cors")
public class CorsProperties {

    @NotEmpty
    private List<@NotBlank String> allowedOrigins = new ArrayList<>();

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @AssertTrue(message = "wbe.cors.allowed-origins 必须是明确来源，不能包含通配符")
    public boolean isWithoutWildcards() {
        return allowedOrigins != null
                && allowedOrigins.stream().noneMatch(origin -> origin != null && origin.contains("*"));
    }
}
