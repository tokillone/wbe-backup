package com.licong.webbackup.config;

import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class DataUploadSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    public DataUploadSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initialize() {
        ensureUsersPermissionColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_batches (
                    upload_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    file_name VARCHAR(255) NOT NULL,
                    stored_file_path VARCHAR(500),
                    sha256 VARCHAR(64) NOT NULL,
                    uploaded_by BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    total_rows INT NOT NULL DEFAULT 0,
                    valid_rows INT NOT NULL DEFAULT 0,
                    error_rows INT NOT NULL DEFAULT 0,
                    warning_rows INT NOT NULL DEFAULT 0,
                    synced_rows INT NOT NULL DEFAULT 0,
                    duplicate_message VARCHAR(500),
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    synced_at DATETIME NULL,
                    reviewed_by BIGINT NULL,
                    reviewed_at DATETIME NULL,
                    review_action VARCHAR(32) NULL,
                    review_note VARCHAR(500) NULL,
                    synced_by BIGINT NULL,
                    INDEX idx_data_upload_user (uploaded_by),
                    INDEX idx_data_upload_status (status),
                    INDEX idx_data_upload_sha (sha256),
                    INDEX idx_data_upload_reviewed_by (reviewed_by),
                    INDEX idx_data_upload_synced_by (synced_by),
                    CONSTRAINT fk_data_upload_user FOREIGN KEY (uploaded_by)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据上传批次审计表'
                """);
        ensureDataUploadBatchAuditColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_rows (
                    row_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    excel_row_number INT NOT NULL,
                    row_status VARCHAR(32) NOT NULL,
                    raw_json LONGTEXT NOT NULL,
                    error_json LONGTEXT,
                    warning_json LONGTEXT,
                    synced_measurement_id BIGINT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_data_upload_rows_batch (upload_id, excel_row_number),
                    INDEX idx_data_upload_rows_status (row_status),
                    CONSTRAINT fk_data_upload_rows_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据上传原始行与校验结果表'
                """);
    }

    private void ensureDataUploadBatchAuditColumns() {
        ensureDataUploadBatchColumn("reviewed_by", "BIGINT NULL COMMENT '审核人'");
        ensureDataUploadBatchColumn("reviewed_at", "DATETIME NULL COMMENT '审核时间'");
        ensureDataUploadBatchColumn("review_action", "VARCHAR(32) NULL COMMENT '审核动作'");
        ensureDataUploadBatchColumn("review_note", "VARCHAR(500) NULL COMMENT '审核备注'");
        ensureDataUploadBatchColumn("synced_by", "BIGINT NULL COMMENT '同步人'");
    }

    private void ensureDataUploadBatchColumn(String columnName, String columnDefinition) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'data_upload_batches'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE data_upload_batches ADD COLUMN " + columnName + " " + columnDefinition);
    }

    private void ensureUsersPermissionColumns() {
        ensureUsersPermissionColumn("can_upload", "BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许上传数据'", true);
        ensureUsersPermissionColumn("can_review_uploads", "BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许审核上传批次'", true);
        ensureUsersPermissionColumn("can_sync_data", "BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否允许同步数据入库'", true);
        ensureUsersPermissionColumn("can_download", "BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否允许下载'", false);
    }

    private void ensureUsersPermissionColumn(String columnName, String columnDefinition, boolean managerDefaultEnabled) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'users'
                  AND column_name = ?
                """, Integer.class, columnName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN " + columnName + " " + columnDefinition);
        if (managerDefaultEnabled) {
            jdbcTemplate.update("UPDATE users SET " + columnName + " = TRUE WHERE role IN ('admin', 'editor')");
        }
    }
}
