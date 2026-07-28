package com.licong.webbackup.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.nio.file.Path;

@Component
@ConfigurationProperties(prefix = "wbe.storage")
public class DataUploadStorageProperties {

    private Path uploadDir = Path.of(System.getProperty("user.dir"), "uploads", "data");
    private DataSize maxFileSize = DataSize.ofMegabytes(50);

    public Path getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(Path uploadDir) {
        this.uploadDir = uploadDir;
    }

    public DataSize getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(DataSize maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public Path normalizedUploadDir() {
        if (uploadDir == null) {
            throw new IllegalStateException("wbe.storage.upload-dir 不能为空");
        }
        return uploadDir.toAbsolutePath().normalize();
    }
}
