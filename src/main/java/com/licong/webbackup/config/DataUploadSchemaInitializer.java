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
        ensureWorkbookUploadSchema();
        jdbcTemplate.update("""
                UPDATE data_upload_batches
                SET status = 'PENDING_REVIEW'
                WHERE status = 'PREVIEWED'
                """);
        ensureBusinessUploadTraceSchema();
    }

    private void ensureWorkbookUploadSchema() {
        ensureTableColumn("data_upload_rows", "sheet_name",
                "VARCHAR(120) NOT NULL DEFAULT '数据表' COMMENT '来源工作表'");
        ensureTableColumn("data_upload_rows", "synced_entity_type",
                "VARCHAR(40) NULL COMMENT '同步后的实体类型'");
        ensureTableColumn("data_upload_rows", "synced_entity_id",
                "BIGINT NULL COMMENT '同步后的实体ID'");
        ensureIndex("data_upload_rows", "idx_data_upload_rows_sheet", """
                CREATE INDEX idx_data_upload_rows_sheet
                ON data_upload_rows(upload_id, sheet_name, excel_row_number)
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS literatures (
                    literature_code VARCHAR(50) PRIMARY KEY COMMENT '文献编号',
                    title LONGTEXT NULL COMMENT '文献名',
                    doi VARCHAR(200) NULL COMMENT 'DOI',
                    keywords LONGTEXT NULL,
                    abstract LONGTEXT NULL,
                    upload_id BIGINT NULL COMMENT '来源上传批次',
                    upload_row_id BIGINT NULL COMMENT '来源上传行',
                    raw_payload LONGTEXT NULL COMMENT '原始行JSON',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_literatures_doi (doi),
                    INDEX idx_literatures_upload (upload_id, upload_row_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献基础信息表'
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS home_target_records (
                    record_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    literature_id VARCHAR(50) NOT NULL,
                    doi VARCHAR(200) NOT NULL,
                    target_category VARCHAR(100) NOT NULL,
                    target_group VARCHAR(20) NOT NULL,
                    substance_category VARCHAR(100) NOT NULL,
                    substance_subclass VARCHAR(100) NOT NULL,
                    biomarker_name VARCHAR(300) NOT NULL,
                    source_sheet VARCHAR(64) NOT NULL DEFAULT '数据表',
                    source_row_number INT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_home_target_source_row (source_sheet, source_row_number),
                    INDEX idx_home_target_group_category (target_group, substance_category),
                    INDEX idx_home_target_category (substance_category),
                    INDEX idx_home_target_subclass (substance_category, substance_subclass),
                    INDEX idx_home_target_biomarker (substance_category, substance_subclass, biomarker_name),
                    INDEX idx_home_target_doi (doi)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='首页目标物质研究图逐行事实表'
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS icd11_sankey_paths (
                    sankey_path_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    target_category VARCHAR(160) NOT NULL,
                    substance_category VARCHAR(180) NOT NULL,
                    substance_subclass VARCHAR(180) NOT NULL,
                    drug_name VARCHAR(300) NOT NULL,
                    indication_original TEXT,
                    biomarker_name VARCHAR(300) NOT NULL,
                    biomarker_alias VARCHAR(300),
                    normalized_indication VARCHAR(300),
                    disease_entity VARCHAR(300),
                    icd11_level1_code VARCHAR(80),
                    icd11_level1_name VARCHAR(220) NOT NULL,
                    icd11_level2_code VARCHAR(80),
                    icd11_level2_name VARCHAR(220) NOT NULL,
                    icd11_level3_code VARCHAR(80),
                    icd11_level3_name VARCHAR(220),
                    mapping_level VARCHAR(80),
                    match_type VARCHAR(180),
                    in_sankey BOOLEAN NOT NULL DEFAULT TRUE,
                    exclusion_reason TEXT,
                    review_status VARCHAR(120),
                    note TEXT,
                    biomarker_cas VARCHAR(80),
                    literature_count DECIMAL(18,4) NOT NULL DEFAULT 1,
                    data_row_count BIGINT NOT NULL DEFAULT 0,
                    unique_doi_count INT NOT NULL DEFAULT 0,
                    missing_doi_count INT NOT NULL DEFAULT 0,
                    upload_id BIGINT NULL,
                    upload_row_id BIGINT NULL,
                    raw_payload LONGTEXT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_icd11_sankey_category (target_category),
                    INDEX idx_icd11_sankey_level1 (target_category, icd11_level1_name),
                    INDEX idx_icd11_sankey_level2 (target_category, icd11_level2_name),
                    INDEX idx_icd11_sankey_drug (target_category, drug_name),
                    INDEX idx_icd11_sankey_biomarker (target_category, biomarker_name)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ICD11疾病映射与可变层级桑基图路径源表'
                """);
        ensureTableColumn("icd11_sankey_paths", "in_sankey",
                "BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否进入桑基图'");
        ensureTableColumn("icd11_sankey_paths", "exclusion_reason",
                "TEXT NULL COMMENT '不入图原因'");
        ensureTableColumn("icd11_sankey_paths", "unique_doi_count",
                "INT NOT NULL DEFAULT 0 COMMENT '唯一DOI数'");
        ensureTableColumn("icd11_sankey_paths", "missing_doi_count",
                "INT NOT NULL DEFAULT 0 COMMENT 'DOI缺失数'");
        ensureTableColumn("icd11_sankey_paths", "upload_id",
                "BIGINT NULL COMMENT '来源上传批次'");
        ensureTableColumn("icd11_sankey_paths", "upload_row_id",
                "BIGINT NULL COMMENT '来源上传行'");
        ensureTableColumn("icd11_sankey_paths", "raw_payload",
                "LONGTEXT NULL COMMENT '映射表原始行JSON'");
        ensureIndex("icd11_sankey_paths", "idx_icd11_sankey_level3", """
                CREATE INDEX idx_icd11_sankey_level3
                ON icd11_sankey_paths(target_category, icd11_level3_name)
                """);
        ensureIndex("icd11_sankey_paths", "idx_icd11_sankey_mapping_depth", """
                CREATE INDEX idx_icd11_sankey_mapping_depth
                ON icd11_sankey_paths(in_sankey, mapping_level)
                """);
        ensureIndex("icd11_sankey_paths", "idx_icd11_sankey_upload", """
                CREATE INDEX idx_icd11_sankey_upload
                ON icd11_sankey_paths(upload_id, upload_row_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS icd11_sankey_path_sources (
                    source_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    sankey_path_id BIGINT NOT NULL,
                    source_order INT NOT NULL,
                    literature_code VARCHAR(50) NULL,
                    doi VARCHAR(200) NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_icd11_path_source_order (sankey_path_id, source_order),
                    INDEX idx_icd11_path_source_literature (literature_code),
                    INDEX idx_icd11_path_source_doi (doi),
                    CONSTRAINT fk_icd11_path_source_path FOREIGN KEY (sankey_path_id)
                        REFERENCES icd11_sankey_paths(sankey_path_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='ICD11映射路径来源文献与DOI'
                """);
        jdbcTemplate.execute("""
                ALTER TABLE icd11_sankey_paths
                COMMENT = 'ICD11疾病映射与可变层级桑基图路径源表'
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

    private void ensureBusinessUploadTraceSchema() {
        if (tableExists("measurements")) {
            ensureTableColumn("measurements", "upload_id", "BIGINT NULL COMMENT '来源上传批次'");
            ensureTableColumn("measurements", "upload_row_id", "BIGINT NULL COMMENT '来源上传行'");
            ensureTableColumn("measurements", "literature_code", "VARCHAR(255) NULL COMMENT '文献编号'");
            ensureTableColumn("measurements", "raw_payload", "LONGTEXT NULL COMMENT '上传行原始JSON'");
            ensureTableColumn("measurements", "dedupe_key", "VARCHAR(128) NULL COMMENT '业务重复判断键'");
            ensureIndex("measurements", "idx_measurements_upload_trace", """
                    CREATE INDEX idx_measurements_upload_trace
                    ON measurements(upload_id, upload_row_id)
                    """);
            ensureIndex("measurements", "idx_measurements_literature_code", """
                    CREATE INDEX idx_measurements_literature_code
                    ON measurements(literature_code)
                    """);
            ensureIndex("measurements", "uk_measurements_dedupe_key", """
                    CREATE UNIQUE INDEX uk_measurements_dedupe_key
                    ON measurements(dedupe_key)
                    """);
        }

        if (tableExists("compounds")) {
            dropIndexIfExists("compounds", "uk_drug_name");
            ensureIndex("compounds", "idx_compound_upload_signature", """
                    CREATE INDEX idx_compound_upload_signature
                    ON compounds(
                        drug_name(80),
                        target_category(60),
                        substance_category(60),
                        substance_subclass(60),
                        biomarker_name(80),
                        biomarker_cas(50)
                    )
                    """);
        }

        if (tableExists("analytical_methods")) {
            dropIndexIfExists("analytical_methods", "uk_method");
            ensureIndex("analytical_methods", "idx_method_upload_signature", """
                    CREATE INDEX idx_method_upload_signature
                    ON analytical_methods(
                        sampling_method,
                        analysis_method,
                        mdl_value,
                        mdl_unit,
                        mql_value,
                        mql_unit,
                        idl_value,
                        idl_unit,
                        iql_value,
                        iql_unit
                    )
                    """);
        }

        if (tableExists("wastewater_plants")) {
            dropIndexIfExists("wastewater_plants", "uk_plant_name");
            ensureIndex("wastewater_plants", "uk_plant_location", """
                    CREATE UNIQUE INDEX uk_plant_location
                    ON wastewater_plants(plant_name(120), country(80), province(80), city(80))
                    """);
        }
    }

    private boolean tableExists(String tableName) {
        Integer tableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                """, Integer.class, tableName);
        return tableCount != null && tableCount > 0;
    }

    private void ensureTableColumn(String tableName, String columnName, String columnDefinition) {
        Integer columnCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND column_name = ?
                """, Integer.class, tableName, columnName);
        if (columnCount != null && columnCount > 0) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
    }

    private void ensureIndex(String tableName, String indexName, String createSql) {
        if (indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute(createSql);
    }

    private void dropIndexIfExists(String tableName, String indexName) {
        if (!indexExists(tableName, indexName)) {
            return;
        }
        jdbcTemplate.execute("DROP INDEX " + indexName + " ON " + tableName);
    }

    private boolean indexExists(String tableName, String indexName) {
        Integer indexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.statistics
                WHERE table_schema = DATABASE()
                  AND table_name = ?
                  AND index_name = ?
                """, Integer.class, tableName, indexName);
        return indexCount != null && indexCount > 0;
    }
}
