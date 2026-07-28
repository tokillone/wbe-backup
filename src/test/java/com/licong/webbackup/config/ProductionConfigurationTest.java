package com.licong.webbackup.config;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

class ProductionConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "DB_URL=jdbc:mysql://db:3306/wbe?sslMode=VERIFY_IDENTITY",
                    "DB_USERNAME=wbe",
                    "DB_PASSWORD=test-only-password",
                    "REDIS_HOST=redis",
                    "REDIS_PORT=6379",
                    "MAIL_HOST=smtp.example.test",
                    "MAIL_PORT=587",
                    "MAIL_USERNAME=wbe@example.test",
                    "MAIL_PASSWORD=test-only-mail-password",
                    "WBE_UPLOAD_DIR=target/test-uploads",
                    "CORS_ALLOWED_ORIGINS=https://wbe.example.test,https://admin.example.test",
                    "TRUSTED_PROXY_IP_REGEX=127\\.0\\.0\\.1",
                    "LOG_FILE=target/test-logs/wbe-backup.log"
            );

    @Test
    void loadsProductionSettingsFromExternalProperties() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getEnvironment().getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:mysql://db:3306/wbe?sslMode=VERIFY_IDENTITY");
            assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.maximum-pool-size"))
                    .isEqualTo("10");
            HikariConfig hikariConfig = Binder.get(context.getEnvironment())
                    .bind("spring.datasource.hikari", Bindable.of(HikariConfig.class))
                    .orElseThrow(() -> new AssertionError("HikariCP production settings were not bound"));
            assertThat(hikariConfig.getMaximumPoolSize()).isEqualTo(10);
            assertThat(hikariConfig.getMinimumIdle()).isEqualTo(2);
            assertThat(hikariConfig.getConnectionTimeout()).isEqualTo(30_000);
            assertThat(context.getEnvironment().getProperty("management.endpoints.web.exposure.include"))
                    .isEqualTo("health,info");
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-details"))
                    .isEqualTo("never");
            assertThat(context.getEnvironment().getProperty("management.endpoint.health.show-components"))
                    .isEqualTo("never");
            assertThat(context.getBean(CorsProperties.class).getAllowedOrigins())
                    .containsExactly("https://wbe.example.test", "https://admin.example.test");
        });
    }

    @Test
    void rejectsBlankProductionDatabasePassword() {
        contextRunner
                .withPropertyValues("DB_PASSWORD=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("spring.datasource.password");
                });
    }

    @Test
    void rejectsBlankProductionUploadDirectory() {
        contextRunner
                .withPropertyValues("WBE_UPLOAD_DIR=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("wbe.storage.upload-dir");
                });
    }

    @Test
    void rejectsWildcardCorsOrigin() {
        contextRunner
                .withPropertyValues("CORS_ALLOWED_ORIGINS=*")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasStackTraceContaining("wbe.cors.allowed-origins");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CorsProperties.class)
    @Import(ProductionConfigurationValidator.class)
    static class TestConfiguration {
    }
}
