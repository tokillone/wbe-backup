package com.licong.webbackup.config;

import com.licong.webbackup.service.ReportedSiteIdentity;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        ensureReviewWorkflowSchema();
        ensureSimplifiedSubmissionWorkflowSchema();
        ensureWorkbookUploadSchema();
        jdbcTemplate.update("""
                UPDATE data_upload_batches
                SET status = 'PENDING_REVIEW'
                WHERE status = 'PREVIEWED'
                """);
        ensureBusinessUploadTraceSchema();
        ensureReportedSiteSchema();
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
                    doi VARCHAR(200) NULL,
                    target_category VARCHAR(100) NOT NULL,
                    target_group VARCHAR(20) NOT NULL,
                    substance_category VARCHAR(100) NOT NULL,
                    substance_subclass VARCHAR(100) NOT NULL,
                    substance_fine VARCHAR(180) NULL COMMENT '目标物质细类',
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
                    substance_fine VARCHAR(180) NULL COMMENT '目标物质细类',
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
        ensureTableColumn("home_target_records", "substance_fine",
                "VARCHAR(180) NULL COMMENT '目标物质细类'");
        ensureTableColumn("icd11_sankey_paths", "substance_fine",
                "VARCHAR(180) NULL COMMENT '目标物质细类'");
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

    private void ensureReviewWorkflowSchema() {
        ensureDataUploadBatchColumn("source_reviewed_by", "BIGINT NULL COMMENT '原始提交初审人'");
        ensureDataUploadBatchColumn("source_reviewed_at", "DATETIME NULL COMMENT '原始提交初审时间'");
        ensureDataUploadBatchColumn("source_review_note", "VARCHAR(500) NULL COMMENT '原始提交初审备注'");
        ensureDataUploadBatchColumn("current_package_id", "BIGINT NULL COMMENT '当前完整整理包'");
        ensureDataUploadBatchColumn("approved_package_id", "BIGINT NULL COMMENT '终审通过的完整整理包'");
        ensureDataUploadBatchColumn("review_checklist_json", "LONGTEXT NULL COMMENT '终审检查项快照'");
        ensureDataUploadBatchColumn("review_diff_json", "LONGTEXT NULL COMMENT '终审时生产差异摘要'");
        ensureDataUploadBatchColumn("sync_error_message", "VARCHAR(500) NULL COMMENT '最近一次同步失败原因'");

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_review_packages (
                    package_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    stored_file_path VARCHAR(500) NULL,
                    sha256 VARCHAR(64) NOT NULL,
                    uploaded_by BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    total_rows INT NOT NULL DEFAULT 0,
                    valid_rows INT NOT NULL DEFAULT 0,
                    error_rows INT NOT NULL DEFAULT 0,
                    warning_rows INT NOT NULL DEFAULT 0,
                    validation_message LONGTEXT NULL,
                    diff_json LONGTEXT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_review_package_version (upload_id, version_no),
                    INDEX idx_review_package_batch (upload_id, created_at),
                    INDEX idx_review_package_sha (sha256),
                    INDEX idx_review_package_uploader (uploaded_by),
                    CONSTRAINT fk_review_package_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE,
                    CONSTRAINT fk_review_package_user FOREIGN KEY (uploaded_by)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上传批次完整整理包版本'
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_audit_events (
                    event_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    package_id BIGINT NULL,
                    action VARCHAR(48) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    from_status VARCHAR(32) NULL,
                    to_status VARCHAR(32) NULL,
                    note VARCHAR(500) NULL,
                    detail_json LONGTEXT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_upload_audit_batch (upload_id, created_at),
                    INDEX idx_upload_audit_package (package_id),
                    INDEX idx_upload_audit_actor (actor_id),
                    CONSTRAINT fk_upload_audit_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE,
                    CONSTRAINT fk_upload_audit_package FOREIGN KEY (package_id)
                        REFERENCES data_upload_review_packages(package_id) ON DELETE SET NULL,
                    CONSTRAINT fk_upload_audit_actor FOREIGN KEY (actor_id)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据上传工作流审计事件'
                """);

        ensureTableColumn("data_upload_rows", "row_stage",
                "VARCHAR(24) NOT NULL DEFAULT 'SUBMISSION' COMMENT '提交数据或整理包数据'");
        ensureTableColumn("data_upload_rows", "review_package_id",
                "BIGINT NULL COMMENT '完整整理包版本'");
        ensureTableColumn("data_upload_rows", "row_fingerprint",
                "VARCHAR(64) NULL COMMENT '稳定行指纹'");
        ensureIndex("data_upload_rows", "idx_data_upload_rows_stage", """
                CREATE INDEX idx_data_upload_rows_stage
                ON data_upload_rows(upload_id, row_stage, review_package_id, sheet_name, excel_row_number)
                """);
        ensureIndex("data_upload_rows", "idx_data_upload_rows_fingerprint", """
                CREATE INDEX idx_data_upload_rows_fingerprint
                ON data_upload_rows(upload_id, row_stage, row_fingerprint)
                """);
    }

    private void ensureSimplifiedSubmissionWorkflowSchema() {
        ensureDataUploadBatchColumn("current_revision_no",
                "INT NOT NULL DEFAULT 1 COMMENT '当前投稿版本'");
        ensureDataUploadBatchColumn("published_release_id",
                "BIGINT NULL COMMENT '成功发布的数据集版本'");
        ensureTableColumn("data_upload_rows", "submission_row_id",
                "VARCHAR(64) NULL COMMENT '跨投稿版本保持不变的行ID'");
        ensureTableColumn("data_upload_rows", "submission_version",
                "INT NOT NULL DEFAULT 1 COMMENT '投稿版本号'");
        ensureIndex("data_upload_rows", "idx_submission_active_rows", """
                CREATE INDEX idx_submission_active_rows
                ON data_upload_rows(upload_id, row_stage, submission_version, submission_row_id)
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_submission_revisions (
                    revision_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    stored_file_path VARCHAR(500) NOT NULL,
                    sha256 VARCHAR(64) NOT NULL,
                    submitted_by BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    total_rows INT NOT NULL DEFAULT 0,
                    valid_rows INT NOT NULL DEFAULT 0,
                    error_rows INT NOT NULL DEFAULT 0,
                    validation_message LONGTEXT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_submission_revision (upload_id, version_no),
                    INDEX idx_submission_revision_sha (sha256),
                    CONSTRAINT fk_submission_revision_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE,
                    CONSTRAINT fk_submission_revision_user FOREIGN KEY (submitted_by)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='普通用户投稿版本'
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS data_upload_field_changes (
                    change_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    package_id BIGINT NOT NULL,
                    submission_row_id VARCHAR(64) NOT NULL,
                    field_name VARCHAR(160) NOT NULL,
                    old_value LONGTEXT NULL,
                    new_value LONGTEXT NULL,
                    reason VARCHAR(500) NOT NULL,
                    changed_by BIGINT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_field_change_row (upload_id, submission_row_id),
                    INDEX idx_field_change_package (package_id),
                    CONSTRAINT fk_field_change_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE,
                    CONSTRAINT fk_field_change_package FOREIGN KEY (package_id)
                        REFERENCES data_upload_review_packages(package_id) ON DELETE CASCADE,
                    CONSTRAINT fk_field_change_user FOREIGN KEY (changed_by)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核字段级纠正审计'
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS dataset_releases (
                    release_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    package_id BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    published_by BIGINT NOT NULL,
                    inserted_records INT NOT NULL DEFAULT 0,
                    skipped_records INT NOT NULL DEFAULT 0,
                    manifest_json LONGTEXT NULL,
                    error_message VARCHAR(500) NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    published_at DATETIME NULL,
                    UNIQUE KEY uk_dataset_release_upload (upload_id),
                    INDEX idx_dataset_release_status (status),
                    CONSTRAINT fk_dataset_release_batch FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_dataset_release_package FOREIGN KEY (package_id)
                        REFERENCES data_upload_review_packages(package_id) ON DELETE RESTRICT,
                    CONSTRAINT fk_dataset_release_user FOREIGN KEY (published_by)
                        REFERENCES users(user_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='增量数据集发布版本'
                """);
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
            ensureTableColumn("measurements", "record_key", "VARCHAR(128) NULL COMMENT '跨批次稳定业务键'");
            ensureTableColumn("measurements", "dataset_release_id", "BIGINT NULL COMMENT '发布版本'");
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
            ensureIndex("measurements", "uk_measurements_record_key", """
                    CREATE UNIQUE INDEX uk_measurements_record_key
                    ON measurements(record_key)
                    """);
            ensureIndex("measurements", "idx_measurements_release", """
                    CREATE INDEX idx_measurements_release
                    ON measurements(dataset_release_id)
                    """);
        }

        if (tableExists("home_target_records")) {
            makeHomeTargetDoiNullable();
            ensureTableColumn("home_target_records", "published_measurement_id",
                    "BIGINT NULL COMMENT '对应正式测量记录'");
            dropIndexIfExists("home_target_records", "uk_home_target_source_row");
            ensureIndex("home_target_records", "uk_home_target_measurement", """
                    CREATE UNIQUE INDEX uk_home_target_measurement
                    ON home_target_records(published_measurement_id)
                    """);
        }

        if (tableExists("compounds")) {
            ensureTableColumn("compounds", "substance_fine",
                    "VARCHAR(180) NULL COMMENT '目标物质细类'");
            dropIndexIfExists("compounds", "uk_drug_name");
            dropIndexIfExists("compounds", "idx_compound_upload_signature");
            ensureIndex("compounds", "idx_compound_upload_signature", """
                    CREATE INDEX idx_compound_upload_signature
                    ON compounds(
                        drug_name(80),
                        target_category(60),
                        substance_category(60),
                        substance_subclass(60),
                        substance_fine(60),
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

    private void makeHomeTargetDoiNullable() {
        Integer nullableCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = DATABASE()
                  AND table_name = 'home_target_records'
                  AND column_name = 'doi'
                  AND is_nullable = 'YES'
                """, Integer.class);
        if (nullableCount == null || nullableCount == 0) {
            jdbcTemplate.execute("ALTER TABLE home_target_records MODIFY COLUMN doi VARCHAR(200) NULL");
        }
    }

    private void ensureReportedSiteSchema() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS confirmed_sites (
                    confirmed_site_id VARCHAR(80) PRIMARY KEY,
                    canonical_name VARCHAR(500) NULL,
                    country VARCHAR(120) NOT NULL,
                    province VARCHAR(120) NULL,
                    city VARCHAR(120) NULL,
                    detailed_address VARCHAR(500) NULL,
                    latitude DECIMAL(12,8) NULL,
                    longitude DECIMAL(12,8) NULL,
                    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                    confirmation_evidence TEXT NOT NULL,
                    confirmed_by BIGINT NULL,
                    confirmed_at DATETIME NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_confirmed_sites_country (country),
                    INDEX idx_confirmed_sites_coordinates (latitude, longitude)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='人工确认的真实污水厂主表'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reported_sites (
                    reported_site_key CHAR(64) PRIMARY KEY,
                    literature_code VARCHAR(255) NOT NULL,
                    raw_plant_name VARCHAR(500) NULL,
                    sampling_site_code VARCHAR(255) NULL,
                    country VARCHAR(120) NULL,
                    province VARCHAR(120) NULL,
                    city VARCHAR(120) NULL,
                    detailed_address VARCHAR(500) NULL,
                    latitude DECIMAL(12,8) NULL,
                    longitude DECIMAL(12,8) NULL,
                    key_quality VARCHAR(24) NOT NULL,
                    confirmed_site_id VARCHAR(80) NULL,
                    confirmation_evidence TEXT NULL,
                    confirmed_by BIGINT NULL,
                    confirmed_at DATETIME NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    INDEX idx_reported_sites_literature (literature_code),
                    INDEX idx_reported_sites_confirmed (confirmed_site_id),
                    INDEX idx_reported_sites_geo (country, province, city),
                    CONSTRAINT fk_reported_sites_confirmed FOREIGN KEY (confirmed_site_id)
                        REFERENCES confirmed_sites(confirmed_site_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文献内报告的污水厂或采样点位'
                """);
        ensureTableColumn("reported_sites", "doi",
                "VARCHAR(255) NULL COMMENT '点位关联表中的 DOI'");
        ensureTableColumn("reported_sites", "canonical_plant_name",
                "VARCHAR(500) NULL COMMENT '规范污水厂名称，仅用于展示和同文献精确关联'");
        ensureTableColumn("reported_sites", "include_in_point_count",
                "BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否计入地图点位覆盖数'");
        ensureTableColumn("reported_sites", "site_note",
                "TEXT NULL COMMENT '点位说明或排除原因'");
        ensureTableColumn("reported_sites", "upload_id",
                "BIGINT NULL COMMENT '来源上传批次'");
        ensureTableColumn("reported_sites", "upload_row_id",
                "BIGINT NULL COMMENT '来源上传行'");
        ensureTableColumn("reported_sites", "excel_row_number",
                "INT NULL COMMENT '点位关联表原始行号'");
        ensureIndex("reported_sites", "idx_reported_sites_upload", """
                CREATE INDEX idx_reported_sites_upload
                ON reported_sites(upload_id, upload_row_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS reported_site_confirmation_audit (
                    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    reported_site_key CHAR(64) NOT NULL,
                    previous_confirmed_site_id VARCHAR(80) NULL,
                    confirmed_site_id VARCHAR(80) NOT NULL,
                    confirmation_evidence TEXT NOT NULL,
                    reviewed_by BIGINT NULL,
                    reviewed_at DATETIME NOT NULL,
                    upload_id BIGINT NULL,
                    action VARCHAR(24) NOT NULL DEFAULT 'CONFIRM',
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    INDEX idx_site_confirmation_reported (reported_site_key, reviewed_at),
                    INDEX idx_site_confirmation_confirmed (confirmed_site_id),
                    CONSTRAINT fk_site_confirmation_reported FOREIGN KEY (reported_site_key)
                        REFERENCES reported_sites(reported_site_key) ON DELETE RESTRICT,
                    CONSTRAINT fk_site_confirmation_confirmed FOREIGN KEY (confirmed_site_id)
                        REFERENCES confirmed_sites(confirmed_site_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跨文献真实点位人工确认审计'
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS approved_confirmed_site_ids (
                    confirmed_site_id VARCHAR(80) PRIMARY KEY,
                    approved_by BIGINT NULL,
                    approved_at DATETIME NOT NULL,
                    approval_note TEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    CONSTRAINT fk_approved_confirmed_site FOREIGN KEY (confirmed_site_id)
                        REFERENCES confirmed_sites(confirmed_site_id) ON DELETE RESTRICT
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='允许跨文献合并的人工批准白名单（当前功能关闭）'
                """);
        if (tableExists("measurements")) {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS record_site_bridge (
                        bridge_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                        upload_id BIGINT NULL,
                        upload_row_id BIGINT NULL,
                        excel_row_number INT NULL,
                        internal_record_key VARCHAR(160) NOT NULL,
                        measurement_id BIGINT NOT NULL,
                        reported_site_key CHAR(64) NULL,
                        effective_site_key VARCHAR(160) NULL,
                        match_status VARCHAR(64) NOT NULL,
                        created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE KEY uk_record_site_bridge (measurement_id, reported_site_key),
                        INDEX idx_record_site_bridge_effective (effective_site_key),
                        INDEX idx_record_site_bridge_status (match_status),
                        INDEX idx_record_site_bridge_upload (upload_id, upload_row_id),
                        CONSTRAINT fk_record_site_bridge_measurement FOREIGN KEY (measurement_id)
                            REFERENCES measurements(measurement_id) ON DELETE CASCADE,
                        CONSTRAINT fk_record_site_bridge_reported FOREIGN KEY (reported_site_key)
                            REFERENCES reported_sites(reported_site_key) ON DELETE RESTRICT
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据记录与点位关联表，可表达零、一或多个点位'
                    """);
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS site_link_import_qc (
                    qc_id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    upload_id BIGINT NOT NULL,
                    merge_confirmed_cross_document_sites BOOLEAN NOT NULL DEFAULT FALSE,
                    site_rows INT NOT NULL DEFAULT 0,
                    included_sites INT NOT NULL DEFAULT 0,
                    excluded_sites INT NOT NULL DEFAULT 0,
                    mapped_sites INT NOT NULL DEFAULT 0,
                    unmapped_sites INT NOT NULL DEFAULT 0,
                    record_rows INT NOT NULL DEFAULT 0,
                    exact_records INT NOT NULL DEFAULT 0,
                    multi_site_records INT NOT NULL DEFAULT 0,
                    location_fallback_records INT NOT NULL DEFAULT 0,
                    excluded_records INT NOT NULL DEFAULT 0,
                    unmatched_country_records INT NOT NULL DEFAULT 0,
                    unmatched_records INT NOT NULL DEFAULT 0,
                    report_json LONGTEXT NOT NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE KEY uk_site_link_qc_upload (upload_id),
                    CONSTRAINT fk_site_link_qc_upload FOREIGN KEY (upload_id)
                        REFERENCES data_upload_batches(upload_id) ON DELETE CASCADE
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='点位关联导入质量核查快照'
                """);
        if (tableExists("sampling_events")) {
            ensureTableColumn("sampling_events", "reported_site_key",
                    "CHAR(64) NULL COMMENT '文献内报告点位稳定键'");
            ensureIndex("sampling_events", "idx_sampling_events_reported_site", """
                    CREATE INDEX idx_sampling_events_reported_site
                    ON sampling_events(reported_site_key)
                    """);
        }
    }

    private void backfillReportedSites() {
        if (!tableExists("measurements") || !tableExists("sampling_events") || !tableExists("wastewater_plants")) {
            return;
        }
        List<LegacySiteRow> rows = jdbcTemplate.query("""
                        SELECT se.event_id,
                               COALESCE(NULLIF(TRIM(m.literature_code), ''), '__missing_literature__') AS literature_code,
                               wp.plant_name, wp.country, wp.province, wp.city
                        FROM sampling_events se
                        JOIN measurements m ON m.event_id = se.event_id
                        JOIN wastewater_plants wp ON wp.plant_id = se.plant_id
                        WHERE se.reported_site_key IS NULL OR TRIM(se.reported_site_key) = ''
                        """,
                (rs, rowNum) -> new LegacySiteRow(
                        rs.getLong("event_id"),
                        rs.getString("literature_code"),
                        rs.getString("plant_name"),
                        rs.getString("country"),
                        rs.getString("province"),
                        rs.getString("city")));
        if (rows.isEmpty()) return;

        Map<String, ReportedSiteSeed> sites = new LinkedHashMap<>();
        for (LegacySiteRow row : rows) {
            ReportedSiteIdentity.Identity identity = ReportedSiteIdentity.create(
                    row.literatureCode(), row.country(), row.province(), row.city(), null, row.plantName());
            sites.putIfAbsent(identity.reportedSiteKey(), new ReportedSiteSeed(
                    identity.reportedSiteKey(), row.literatureCode(), row.plantName(), row.country(),
                    row.province(), row.city(), identity.keyQuality()));
        }
        jdbcTemplate.batchUpdate("""
                        INSERT INTO reported_sites (
                            reported_site_key, literature_code, raw_plant_name,
                            country, province, city, key_quality
                        ) VALUES (?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            literature_code = VALUES(literature_code),
                            raw_plant_name = VALUES(raw_plant_name),
                            country = VALUES(country),
                            province = VALUES(province),
                            city = VALUES(city),
                            key_quality = VALUES(key_quality)
                        """,
                sites.values(), sites.size(),
                (ps, site) -> {
                    ps.setString(1, site.reportedSiteKey());
                    ps.setString(2, site.literatureCode());
                    ps.setString(3, site.plantName());
                    ps.setString(4, site.country());
                    ps.setString(5, site.province());
                    ps.setString(6, site.city());
                    ps.setString(7, site.keyQuality());
                });
        jdbcTemplate.batchUpdate("UPDATE sampling_events SET reported_site_key = ? WHERE event_id = ?",
                rows, rows.size(),
                (ps, row) -> {
                    ReportedSiteIdentity.Identity identity = ReportedSiteIdentity.create(
                            row.literatureCode(), row.country(), row.province(), row.city(), null, row.plantName());
                    ps.setString(1, identity.reportedSiteKey());
                    ps.setLong(2, row.eventId());
                });
    }

    private record LegacySiteRow(long eventId, String literatureCode, String plantName,
                                 String country, String province, String city) {
    }

    private record ReportedSiteSeed(String reportedSiteKey, String literatureCode, String plantName,
                                    String country, String province, String city, String keyQuality) {
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
