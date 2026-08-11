package com.licong.webbackup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.licong.webbackup.config.DataUploadStorageProperties;
import com.licong.webbackup.dto.upload.DataUploadBatchPageResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadReviewDecisionRequest;
import com.licong.webbackup.dto.upload.DataUploadReviewPackageResponse;
import com.licong.webbackup.dto.upload.DataUploadRowsPageResponse;
import com.licong.webbackup.dto.upload.DataUploadSourceReviewRequest;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(DataUploadServiceIntegrationTest.TestConfig.class)
class DataUploadServiceIntegrationTest {

    private static final Path TEST_UPLOAD_DIR = Path.of(
            System.getProperty("java.io.tmpdir"),
            "wbe-data-upload-tests-" + UUID.randomUUID()
    ).toAbsolutePath().normalize();

    private final DataUploadService service;
    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    @TempDir
    Path tempDir;

    @Autowired
    DataUploadServiceIntegrationTest(DataUploadService service,
                                     JdbcTemplate jdbcTemplate,
                                     PlatformTransactionManager transactionManager) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionManager = transactionManager;
    }

    @BeforeEach
    void setUpSchema() {
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY,
                    username VARCHAR(100) NOT NULL,
                    email VARCHAR(200),
                    role VARCHAR(20) NOT NULL,
                    can_upload BOOLEAN NOT NULL,
                    can_review_uploads BOOLEAN NOT NULL,
                    can_sync_data BOOLEAN NOT NULL,
                    can_download BOOLEAN NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE data_upload_batches (
                    upload_id BIGINT AUTO_INCREMENT PRIMARY KEY,
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
                    created_at TIMESTAMP,
                    synced_at TIMESTAMP NULL,
                    reviewed_by BIGINT NULL,
                    reviewed_at TIMESTAMP NULL,
                    review_action VARCHAR(32) NULL,
                    review_note VARCHAR(500) NULL,
                    synced_by BIGINT NULL,
                    source_reviewed_by BIGINT NULL,
                    source_reviewed_at TIMESTAMP NULL,
                    source_review_note VARCHAR(500) NULL,
                    current_package_id BIGINT NULL,
                    approved_package_id BIGINT NULL,
                    review_checklist_json CLOB NULL,
                    review_diff_json CLOB NULL,
                    sync_error_message VARCHAR(500) NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE data_upload_review_packages (
                    package_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT NOT NULL,
                    version_no INT NOT NULL,
                    file_name VARCHAR(255) NOT NULL,
                    stored_file_path VARCHAR(500),
                    sha256 VARCHAR(64) NOT NULL,
                    uploaded_by BIGINT NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    total_rows INT NOT NULL DEFAULT 0,
                    valid_rows INT NOT NULL DEFAULT 0,
                    error_rows INT NOT NULL DEFAULT 0,
                    warning_rows INT NOT NULL DEFAULT 0,
                    validation_message CLOB,
                    diff_json CLOB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE (upload_id, version_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE data_upload_audit_events (
                    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT NOT NULL,
                    package_id BIGINT,
                    action VARCHAR(48) NOT NULL,
                    actor_id BIGINT NOT NULL,
                    from_status VARCHAR(32),
                    to_status VARCHAR(32),
                    note VARCHAR(500),
                    detail_json CLOB,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE data_upload_rows (
                    row_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT NOT NULL,
                    review_package_id BIGINT NULL,
                    row_stage VARCHAR(24) NOT NULL DEFAULT 'SUBMISSION',
                    sheet_name VARCHAR(120) NOT NULL,
                    excel_row_number INT NOT NULL,
                    row_status VARCHAR(32) NOT NULL,
                    raw_json CLOB NOT NULL,
                    error_json CLOB,
                    warning_json CLOB,
                    synced_measurement_id BIGINT NULL,
                    synced_entity_type VARCHAR(40) NULL,
                    synced_entity_id BIGINT NULL,
                    row_fingerprint VARCHAR(64) NULL,
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE compounds (
                    compound_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_category VARCHAR(160) NOT NULL,
                    substance_category VARCHAR(180) NOT NULL,
                    substance_subclass VARCHAR(180),
                    substance_fine VARCHAR(180),
                    drug_name VARCHAR(300) NOT NULL,
                    indications CLOB,
                    prescription_type VARCHAR(20),
                    biomarker_name VARCHAR(300),
                    biomarker_cas VARCHAR(80),
                    physicochemical_properties CLOB,
                    calibration_coefficient DECIMAL(15, 6),
                    human_excretion_rate DECIMAL(8, 4),
                    consumption_value DECIMAL(20, 4),
                    consumption_unit VARCHAR(80),
                    usage_prevalence DECIMAL(8, 4),
                    disease_prevalence DECIMAL(8, 4),
                    keywords CLOB,
                    doi VARCHAR(200),
                    abstract CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE analytical_methods (
                    method_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    sampling_method VARCHAR(200),
                    analysis_method VARCHAR(200),
                    mdl_value DECIMAL(20, 6),
                    mdl_unit VARCHAR(30),
                    mql_value DECIMAL(20, 6),
                    mql_unit VARCHAR(30),
                    idl_value DECIMAL(20, 6),
                    idl_unit VARCHAR(30),
                    iql_value DECIMAL(20, 6),
                    iql_unit VARCHAR(30)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE wastewater_plants (
                    plant_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plant_name VARCHAR(500) NOT NULL,
                    treatment_capacity_m3_day BIGINT,
                    served_population BIGINT,
                    country VARCHAR(100),
                    province VARCHAR(100),
                    city VARCHAR(100),
                    gs_attenuation_coefficient DECIMAL(10, 6)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE confirmed_sites (
                    confirmed_site_id VARCHAR(80) PRIMARY KEY,
                    canonical_name VARCHAR(500),
                    country VARCHAR(120) NOT NULL,
                    province VARCHAR(120),
                    city VARCHAR(120),
                    detailed_address VARCHAR(500),
                    latitude DECIMAL(12,8),
                    longitude DECIMAL(12,8),
                    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
                    confirmation_evidence CLOB NOT NULL,
                    confirmed_by BIGINT,
                    confirmed_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE reported_sites (
                    reported_site_key CHAR(64) PRIMARY KEY,
                    literature_code VARCHAR(255) NOT NULL,
                    doi VARCHAR(255),
                    raw_plant_name VARCHAR(500),
                    canonical_plant_name VARCHAR(500),
                    sampling_site_code VARCHAR(255),
                    country VARCHAR(120),
                    province VARCHAR(120),
                    city VARCHAR(120),
                    detailed_address VARCHAR(500),
                    latitude DECIMAL(12,8),
                    longitude DECIMAL(12,8),
                    key_quality VARCHAR(24) NOT NULL,
                    include_in_point_count BOOLEAN NOT NULL DEFAULT TRUE,
                    site_note CLOB,
                    upload_id BIGINT,
                    upload_row_id BIGINT,
                    excel_row_number INT,
                    confirmed_site_id VARCHAR(80),
                    confirmation_evidence CLOB,
                    confirmed_by BIGINT,
                    confirmed_at TIMESTAMP,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE reported_site_confirmation_audit (
                    audit_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    reported_site_key CHAR(64) NOT NULL,
                    previous_confirmed_site_id VARCHAR(80),
                    confirmed_site_id VARCHAR(80) NOT NULL,
                    confirmation_evidence CLOB NOT NULL,
                    reviewed_by BIGINT,
                    reviewed_at TIMESTAMP NOT NULL,
                    upload_id BIGINT,
                    action VARCHAR(24) NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sampling_events (
                    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plant_id BIGINT NOT NULL,
                    reported_site_key CHAR(64),
                    sample_collection_time TIMESTAMP,
                    sampling_start_ym VARCHAR(7),
                    sampling_end_ym VARCHAR(7),
                    source_workbook VARCHAR(255),
                    original_row_number INT
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE measurements (
                    measurement_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    compound_id BIGINT NOT NULL,
                    method_id BIGINT NOT NULL,
                    event_id BIGINT NOT NULL,
                    upload_id BIGINT,
                    upload_row_id BIGINT,
                    literature_code VARCHAR(255),
                    raw_payload CLOB,
                    dedupe_key VARCHAR(128) UNIQUE,
                    plot_concentration_value DECIMAL(20, 6),
                    plot_concentration_unit VARCHAR(30),
                    inflow_min_value DECIMAL(20, 6),
                    inflow_min_unit VARCHAR(30),
                    inflow_max_value DECIMAL(20, 6),
                    inflow_max_unit VARCHAR(30),
                    inflow_avg_value DECIMAL(20, 6),
                    inflow_avg_unit VARCHAR(30),
                    inflow_median_value DECIMAL(20, 6),
                    inflow_median_unit VARCHAR(30),
                    daily_load_dls_value DECIMAL(25, 6),
                    daily_load_dls_unit VARCHAR(30),
                    pndl_value DECIMAL(25, 6),
                    pndl_unit VARCHAR(30),
                    pndl_estimated_value DECIMAL(25, 6),
                    pndl_estimated_unit VARCHAR(30),
                    plot_pndl_value DECIMAL(25, 6),
                    plot_pndl_unit VARCHAR(30)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE record_site_bridge (
                    bridge_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT,
                    upload_row_id BIGINT,
                    excel_row_number INT,
                    internal_record_key VARCHAR(160) NOT NULL,
                    measurement_id BIGINT NOT NULL,
                    reported_site_key CHAR(64),
                    effective_site_key VARCHAR(160),
                    match_status VARCHAR(64) NOT NULL,
                    UNIQUE (measurement_id, reported_site_key)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE site_link_import_qc (
                    qc_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT NOT NULL UNIQUE,
                    merge_confirmed_cross_document_sites BOOLEAN NOT NULL,
                    site_rows INT NOT NULL,
                    included_sites INT NOT NULL,
                    excluded_sites INT NOT NULL,
                    mapped_sites INT NOT NULL,
                    unmapped_sites INT NOT NULL,
                    record_rows INT NOT NULL,
                    exact_records INT NOT NULL,
                    multi_site_records INT NOT NULL,
                    location_fallback_records INT NOT NULL,
                    excluded_records INT NOT NULL,
                    unmatched_country_records INT NOT NULL,
                    unmatched_records INT NOT NULL,
                    report_json CLOB NOT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE literatures (
                    literature_code VARCHAR(50) PRIMARY KEY,
                    title CLOB,
                    doi VARCHAR(200),
                    keywords CLOB,
                    abstract CLOB,
                    upload_id BIGINT,
                    upload_row_id BIGINT,
                    raw_payload CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE home_target_records (
                    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    literature_id VARCHAR(50) NOT NULL,
                    doi VARCHAR(200) NOT NULL,
                    target_category VARCHAR(100) NOT NULL,
                    target_group VARCHAR(20) NOT NULL,
                    substance_category VARCHAR(100) NOT NULL,
                    substance_subclass VARCHAR(100) NOT NULL,
                    substance_fine VARCHAR(180),
                    biomarker_name VARCHAR(300) NOT NULL,
                    source_sheet VARCHAR(64) NOT NULL,
                    source_row_number INT NOT NULL,
                    UNIQUE (source_sheet, source_row_number)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE icd11_sankey_paths (
                    sankey_path_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_category VARCHAR(160) NOT NULL,
                    substance_category VARCHAR(180) NOT NULL,
                    substance_subclass VARCHAR(180) NOT NULL,
                    substance_fine VARCHAR(180),
                    drug_name VARCHAR(300) NOT NULL,
                    indication_original CLOB,
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
                    in_sankey BOOLEAN NOT NULL,
                    exclusion_reason CLOB,
                    review_status VARCHAR(120),
                    note CLOB,
                    biomarker_cas VARCHAR(80),
                    literature_count DECIMAL(18,4) NOT NULL,
                    data_row_count BIGINT NOT NULL,
                    unique_doi_count INT NOT NULL,
                    missing_doi_count INT NOT NULL,
                    upload_id BIGINT,
                    upload_row_id BIGINT,
                    raw_payload CLOB
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE icd11_sankey_path_sources (
                    source_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    sankey_path_id BIGINT NOT NULL,
                    source_order INT NOT NULL,
                    literature_code VARCHAR(50),
                    doi VARCHAR(200),
                    UNIQUE (sankey_path_id, source_order)
                )
                """);
    }

    @AfterEach
    void deleteStoredUploads() {
        List<String> paths = jdbcTemplate.query(
                """
                SELECT stored_file_path
                FROM data_upload_batches
                WHERE stored_file_path IS NOT NULL
                UNION ALL
                SELECT stored_file_path
                FROM data_upload_review_packages
                WHERE stored_file_path IS NOT NULL
                """,
                (rs, rowNum) -> rs.getString(1)
        );
        paths.forEach(path -> {
            try {
                Files.deleteIfExists(Path.of(path));
            } catch (Exception ignored) {
                // Test cleanup must not hide the assertion result.
            }
        });
    }

    @Test
    void uploadAlwaysWaitsForReviewAndDuplicateShaRequiresAdminOverride() throws Exception {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User admin = insertUser(2L, "admin", "admin", false, false, false, false);
        byte[] workbook = validWorkbook("LIT-001");

        DataUploadPreviewResponse preview = service.preview(file(workbook), uploader, false);
        long filesAfterFirstUpload = countUploadFiles();

        assertThat(preview.getBatch().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(preview.getBatch().getErrorRows()).isZero();
        assertThatThrownBy(() -> service.preview(file(workbook), uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相同 SHA256");
        assertThat(countUploadFiles()).isEqualTo(filesAfterFirstUpload);
        assertThatThrownBy(() -> service.preview(file(workbook), uploader, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThat(countUploadFiles()).isEqualTo(filesAfterFirstUpload);

        DataUploadPreviewResponse adminOverride = service.preview(file(workbook), admin, true);
        assertThat(adminOverride.getBatch().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(adminOverride.getBatch().getDuplicateMessage()).contains("相同 SHA256");
        completeReview(adminOverride.getBatch().getUploadId(), admin, workbook);
        assertThat(service.sync(adminOverride.getBatch().getUploadId(), admin).getBatch().getStatus()).isEqualTo("SYNCED");
    }

    @Test
    void validatesEmptyExtensionSizeAndExcelReadabilityWithChineseMessages() throws Exception {
        User uploader = insertUser(1L, "validator", "viewer", true, false, false, true);
        MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx", null, new byte[0]);
        MockMultipartFile wrongExtension = new MockMultipartFile("file", "data.xls", null, new byte[]{1});
        MultipartFile oversized = mock(MultipartFile.class);
        when(oversized.isEmpty()).thenReturn(false);
        when(oversized.getSize()).thenReturn(50L * 1024 * 1024 + 1);

        assertThatThrownBy(() -> service.preview(empty, uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择需要上传");
        assertThatThrownBy(() -> service.preview(wrongExtension, uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅支持 .xlsx");
        assertThatThrownBy(() -> service.preview(oversized, uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能超过 50MB");

        DataUploadPreviewResponse unreadable = service.preview(file("not-an-excel-file".getBytes(StandardCharsets.UTF_8)), uploader, false);

        assertThat(unreadable.getBatch().getStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(unreadable.getHeaderErrors())
                .anyMatch(message -> message.contains("Excel 文件无法读取或格式损坏"));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT stored_file_path FROM data_upload_batches WHERE upload_id = ?",
                String.class,
                unreadable.getBatch().getUploadId()
        )).isNull();
        assertThat(countUploadFiles()).isZero();
    }

    @Test
    void rejectsWorkbookWithValidHeadersButNoBusinessRows() throws Exception {
        User uploader = insertUser(1L, "empty-workbook", "viewer", true, false, false, true);

        DataUploadPreviewResponse preview = service.preview(file(service.createTemplateWorkbook()), uploader, false);

        assertThat(preview.getBatch().getStatus()).isEqualTo("VALIDATION_FAILED");
        assertThat(preview.getBatch().getTotalRows()).isZero();
        assertThat(preview.getHeaderErrors())
                .containsExactly("数据表中至少需要包含一行数据");
    }

    @Test
    void reportsUnwritableUploadDirectoryWithoutLeavingFiles() throws Exception {
        User uploader = insertUser(1L, "directory-check", "viewer", true, false, false, true);
        Path notDirectory = tempDir.resolve("upload-root");
        Files.writeString(notDirectory, "occupied");
        DataUploadStorageProperties properties = new DataUploadStorageProperties();
        properties.setUploadDir(notDirectory);
        DataUploadService isolatedService = new DataUploadService(
                jdbcTemplate,
                new ObjectMapper(),
                mock(DataSource.class),
                properties
        );

        assertThatThrownBy(() -> isolatedService.preview(file(validWorkbook("LIT-DIR")), uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上传目录不可写");
        assertThat(Files.readString(notDirectory)).isEqualTo("occupied");
    }

    @Test
    void storesOnlyUuidFileNameAndKeepsOriginalNameForDisplay() throws Exception {
        User uploader = insertUser(1L, "filename-check", "viewer", true, false, false, true);
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "../../原始数据.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                validWorkbook("LIT-FILENAME")
        );

        DataUploadPreviewResponse preview = service.preview(upload, uploader, false);
        Map<String, Object> stored = jdbcTemplate.queryForMap(
                "SELECT file_name, stored_file_path FROM data_upload_batches WHERE upload_id = ?",
                preview.getBatch().getUploadId()
        );

        assertThat(stored.get("file_name")).isEqualTo("原始数据.xlsx");
        Path storedPath = Path.of((String) stored.get("stored_file_path"));
        assertThat(storedPath.getParent()).isEqualTo(TEST_UPLOAD_DIR);
        assertThat(storedPath.getFileName().toString())
                .matches("[0-9a-fA-F-]{36}\\.xlsx")
                .doesNotContain("原始数据");
    }

    @Test
    void preservesUploadedWorkbookBytesAndRecordedShaDuringPreview() throws Exception {
        User uploader = insertUser(1L, "original-file-check", "viewer", true, false, false, true);
        byte[] uploadBytes = withoutCoreProperties(validWorkbook("LIT-ORIGINAL"));

        DataUploadPreviewResponse preview = service.preview(file(uploadBytes), uploader, false);
        Path storedPath = service.getStoredFile(preview.getBatch().getUploadId(), uploader);

        assertThat(Files.readAllBytes(storedPath)).isEqualTo(uploadBytes);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT sha256 FROM data_upload_batches WHERE upload_id = ?",
                String.class,
                preview.getBatch().getUploadId()
        )).isEqualTo(sha256(uploadBytes));
    }

    @Test
    void previewsCompletePublishedWorkbookWithoutLosingRows() throws Exception {
        Path workbookPath = Path.of("..", "WBE汇总表7.22.xlsx").toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(workbookPath));
        User uploader = insertUser(1L, "publisher", "viewer", true, false, false, true);
        MockMultipartFile workbookFile = new MockMultipartFile(
                "file",
                workbookPath.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(workbookPath)
        );

        DataUploadPreviewResponse result = service.preview(workbookFile, uploader, false);

        assertThat(result.getHeaderErrors()).isEmpty();
        assertThat(result.getBatch().getTotalRows()).isEqualTo(22_738);
        assertThat(result.getBatch().getErrorRows()).isZero();
        assertThat(result.getSheetSummaries())
                .extracting("sheetName", "totalRows")
                .containsExactly(org.assertj.core.groups.Tuple.tuple("数据表", 22_738));
        assertWorkbookRowsRoundTrip(workbookPath, result.getBatch().getUploadId());
    }

    @Test
    void previewsFourLevelWorkbookAndPreservesFineClassification() throws Exception {
        Path workbookPath = Path.of("..", "WBE汇总表7.28.xlsx").toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(workbookPath));
        User uploader = insertUser(1L, "four-level-publisher", "viewer", true, false, false, true);
        MockMultipartFile workbookFile = new MockMultipartFile(
                "file",
                workbookPath.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(workbookPath)
        );

        DataUploadPreviewResponse result = service.preview(workbookFile, uploader, false);

        assertThat(result.getHeaderErrors()).isEmpty();
        assertThat(result.getBatch().getTotalRows()).isEqualTo(22_738);
        assertThat(result.getBatch().getErrorRows()).isZero();
        String firstDataRow = jdbcTemplate.queryForObject("""
                        SELECT raw_json
                        FROM data_upload_rows
                        WHERE upload_id = ? AND sheet_name = '数据表' AND excel_row_number = 2
                        """,
                String.class,
                result.getBatch().getUploadId()
        );
        assertThat(firstDataRow)
                .contains("\"目标物质子类\":\"J01F 大环内酯类、林可酰胺类和链阳菌素类\"")
                .contains("\"目标物质细类\":\"J01FF 林可酰胺类\"");
    }

    @Test
    void publishedWorkbookClosedSliceCompletesWorkflowAndRollbackRestoresDatabaseAndFiles() throws Exception {
        Path slicePath = Path.of(
                "src", "test", "resources", "data-upload", "WBE-7.28-closed-slice.xlsx"
        ).toAbsolutePath().normalize();
        assertThat(slicePath).exists();
        byte[] slice = Files.readAllBytes(slicePath);
        User admin = insertUser(1L, "slice-admin", "admin", false, false, false, true);
        jdbcTemplate.update("""
                INSERT INTO literatures (literature_code, title, doi)
                VALUES ('BASELINE', 'Baseline snapshot', '10.1000/baseline')
                """);
        long filesBefore = countUploadFiles();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            DataUploadPreviewResponse preview = service.preview(file(slice), admin, false);
            assertThat(preview.getBatch().getTotalRows()).isEqualTo(1);
            completeReview(preview.getBatch().getUploadId(), admin, slice);
            DataUploadSyncResponse sync = service.sync(preview.getBatch().getUploadId(), admin);
            assertThat(sync.getInsertedRows()).isEqualTo(6);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM measurements", Integer.class)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM literatures WHERE literature_code = 'WBE0107'",
                    Integer.class)).isEqualTo(1);
            status.setRollbackOnly();
        });

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM literatures WHERE literature_code = 'BASELINE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM literatures WHERE literature_code = 'WBE0107'",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM measurements", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_upload_batches", Integer.class)).isZero();
        assertThat(countUploadFiles()).isEqualTo(filesBefore);
    }

    @Test
    void reviewAndSyncPermissionsEnforceSeparatedWorkflowAndPersistTraceFields() throws Exception {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User reviewer = insertUser(2L, "reviewer", "viewer", false, true, false, true);
        User syncer = insertUser(3L, "syncer", "viewer", false, false, true, true);
        DataUploadPreviewResponse preview = service.preview(file(validWorkbook("LIT-TRACE")), uploader, false);
        Long uploadId = preview.getBatch().getUploadId();

        assertThatThrownBy(() -> service.approve(uploadId, syncer, fullyConfirmedDecision()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThatThrownBy(() -> service.sync(uploadId, syncer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未审核通过");

        completeReview(uploadId, reviewer, validWorkbook("LIT-TRACE"));
        assertThatThrownBy(() -> service.sync(uploadId, reviewer))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        DataUploadSyncResponse result = service.sync(uploadId, syncer);

        assertThat(result.getBatch().getStatus()).isEqualTo("SYNCED");
        assertThat(result.getInsertedRows()).isEqualTo(6);
        assertThat(result.getInsertedRowsBySheet()).containsEntry("数据表", 1)
                .containsEntry("点位关联表", 1)
                .containsEntry("药物疾病ICD11映射", 1)
                .containsEntry("文献基础信息", 1)
                .containsEntry("采样方法统一审计", 1)
                .containsEntry("核心标记物优先级识别", 1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT upload_id FROM measurements WHERE upload_id = ?",
                Long.class,
                uploadId
        )).isEqualTo(uploadId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT upload_row_id FROM measurements WHERE upload_id = ?",
                Long.class,
                uploadId
        )).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT literature_code FROM measurements WHERE upload_id = ?",
                String.class,
                uploadId
        )).isEqualTo("LIT-TRACE");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT raw_payload FROM measurements WHERE upload_id = ?",
                String.class,
                uploadId
        )).contains("LIT-TRACE");
        LocalDateTime collectionTime = jdbcTemplate.queryForObject(
                "SELECT sample_collection_time FROM sampling_events",
                (rs, rowNum) -> rs.getTimestamp(1).toLocalDateTime()
        );
        assertThat(collectionTime).isEqualTo(LocalDateTime.of(2025, 6, 15, 10, 30));
        String reportedSiteKey = jdbcTemplate.queryForObject(
                "SELECT reported_site_key FROM record_site_bridge", String.class);
        assertThat(reportedSiteKey).isEqualTo("LIT-TRACE-S001");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reported_sites WHERE reported_site_key = ? AND literature_code = 'LIT-TRACE'",
                Integer.class,
                reportedSiteKey)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT effective_site_key FROM record_site_bridge", String.class))
                .isEqualTo("reported:LIT-TRACE-S001");
        Map<String, Object> rowState = jdbcTemplate.queryForMap(
                """
                SELECT row_status, synced_measurement_id
                FROM data_upload_rows
                WHERE upload_id = ?
                  AND sheet_name = '数据表'
                  AND row_stage = 'REVIEW_PACKAGE'
                """,
                uploadId
        );
        assertThat(rowState.get("row_status")).isEqualTo("SYNCED");
        assertThat(rowState.get("synced_measurement_id")).isNotNull();
        assertThatThrownBy(() -> service.reject(uploadId, reviewer, "too late"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已入库批次不能驳回");
    }

    @Test
    void reviewPackagesAreVersionedChecklistProtectedAndSeparatedFromSubmissionRows() throws Exception {
        User uploader = insertUser(1L, "package-uploader", "viewer", true, false, false, true);
        User admin = insertUser(2L, "package-admin", "admin", false, false, false, true);
        byte[] workbook = validWorkbook("LIT-PACKAGE");
        Long uploadId = service.preview(file(workbook), uploader, false).getBatch().getUploadId();

        assertThatThrownBy(() -> service.approve(uploadId, admin, fullyConfirmedDecision()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成初审和完整整理包");
        service.acceptSourceReview(uploadId, admin, null);

        DataUploadReviewPackageResponse first =
                service.uploadReviewPackage(uploadId, file(workbook), admin, false);
        assertThat(first.getVersionNo()).isEqualTo(1);
        assertThat(first.getStatus()).isEqualTo("VALID");
        long filesAfterFirstPackage = countUploadFiles();

        assertThatThrownBy(() -> service.uploadReviewPackage(uploadId, file(workbook), admin, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("内容完全相同");
        assertThat(countUploadFiles()).isEqualTo(filesAfterFirstPackage);

        DataUploadReviewPackageResponse second =
                service.uploadReviewPackage(uploadId, file(workbook), admin, true);
        assertThat(second.getVersionNo()).isEqualTo(2);
        assertThat(service.listReviewPackages(uploadId, admin))
                .extracting("versionNo")
                .containsExactly(2, 1);

        DataUploadRowsPageResponse submissionRows =
                service.listRows(uploadId, 1, 20, "all", "submission", admin);
        DataUploadRowsPageResponse packageRows =
                service.listRows(uploadId, 1, 20, "all", "reviewPackage", admin);
        assertThat(submissionRows.getTotal()).isEqualTo(1);
        assertThat(submissionRows.getRows()).allMatch(row -> "SUBMISSION".equals(row.getRowStage()));
        assertThat(packageRows.getTotal()).isEqualTo(6);
        assertThat(packageRows.getReviewPackageId()).isEqualTo(second.getPackageId());
        assertThat(packageRows.getRows()).allMatch(row -> "REVIEW_PACKAGE".equals(row.getRowStage()));

        assertThatThrownBy(() -> service.approve(
                uploadId, admin, new DataUploadReviewDecisionRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("八项");
        assertThat(service.approve(uploadId, admin, fullyConfirmedDecision()).getApprovedPackageId())
                .isEqualTo(second.getPackageId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_upload_audit_events WHERE upload_id = ?",
                Integer.class,
                uploadId
        )).isEqualTo(4);
    }

    @Test
    void highRiskProductionDecreaseRequiresAdministratorFinalApproval() throws Exception {
        User uploader = insertUser(1L, "risk-uploader", "viewer", true, false, false, true);
        User reviewer = insertUser(2L, "risk-reviewer", "viewer", false, true, false, true);
        User admin = insertUser(3L, "risk-admin", "admin", false, false, false, true);
        jdbcTemplate.update("""
                INSERT INTO literatures (literature_code, title, doi)
                VALUES ('OLD-1', 'Old one', '10.1000/old1'),
                       ('OLD-2', 'Old two', '10.1000/old2')
                """);
        byte[] workbook = validWorkbook("LIT-HIGH-RISK");
        Long uploadId = service.preview(file(workbook), uploader, false).getBatch().getUploadId();
        service.acceptSourceReview(uploadId, reviewer, null);
        DataUploadReviewPackageResponse reviewPackage =
                service.uploadReviewPackage(uploadId, file(workbook), reviewer, false);

        assertThat(reviewPackage.getDiffSummary()).containsEntry("riskLevel", "HIGH");
        assertThatThrownBy(() -> service.approve(uploadId, reviewer, fullyConfirmedDecision()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThat(service.approve(uploadId, admin, fullyConfirmedDecision()).getStatus())
                .isEqualTo("APPROVED");
    }

    @Test
    void confirmedSiteWithoutEvidenceRemainsCandidateAndWarnsDuringPreview() throws Exception {
        User admin = insertUser(1L, "site-admin", "admin", false, false, false, true);
        byte[] workbook = workbookWithSiteFields("LIT-SITE-EVIDENCE", Map.of(
                "confirmed_site_id", "CN-SITE-001"
        ));

        DataUploadPreviewResponse preview = service.preview(file(workbook), admin, false);
        service.acceptSourceReview(preview.getBatch().getUploadId(), admin, null);
        DataUploadReviewPackageResponse reviewPackage =
                service.uploadReviewPackage(preview.getBatch().getUploadId(), file(workbook), admin, false);

        assertThat(preview.getBatch().getErrorRows()).isZero();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT warning_json
                FROM data_upload_rows
                WHERE review_package_id = ? AND sheet_name = '点位关联表'
                """, String.class, reviewPackage.getPackageId())).contains("仅作为候选");
    }

    @Test
    void confirmedSiteCannotBeReusedAcrossCountries() throws Exception {
        User admin = insertUser(1L, "site-admin", "admin", false, false, false, true);
        jdbcTemplate.update("""
                INSERT INTO confirmed_sites (
                    confirmed_site_id, canonical_name, country, confirmation_evidence, confirmed_at
                ) VALUES ('SITE-CROSS-COUNTRY', 'Existing site', '新西兰', '人工核查', CURRENT_TIMESTAMP)
                """);
        byte[] workbook = workbookWithSiteFields("LIT-SITE-COUNTRY", Map.of(
                "confirmed_site_id", "SITE-CROSS-COUNTRY",
                "同一污水厂确认依据", "同一官方名称和地址"
        ));

        DataUploadPreviewResponse preview = service.preview(file(workbook), admin, false);
        service.acceptSourceReview(preview.getBatch().getUploadId(), admin, null);
        DataUploadReviewPackageResponse reviewPackage =
                service.uploadReviewPackage(preview.getBatch().getUploadId(), file(workbook), admin, false);

        assertThat(reviewPackage.getErrorRows()).isPositive();
        assertThat(jdbcTemplate.queryForObject("""
                SELECT error_json
                FROM data_upload_rows
                WHERE review_package_id = ? AND sheet_name = '点位关联表'
                """, String.class, reviewPackage.getPackageId())).contains("已归属其他国家");
    }

    @Test
    void confirmedIdPersistsAsCandidateButDoesNotChangeEffectivePointIdentity() throws Exception {
        User admin = insertUser(1L, "site-admin", "admin", false, false, false, true);
        byte[] workbook = workbookWithSiteFields("LIT-SITE-CONFIRMED", Map.of(
                "confirmed_site_id", "CN-NB-0001",
                "同一污水厂确认依据", "官方名称、详细地址和经纬度一致"
        ));
        DataUploadPreviewResponse preview = service.preview(file(workbook), admin, false);
        completeReview(preview.getBatch().getUploadId(), admin, workbook);

        service.sync(preview.getBatch().getUploadId(), admin);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM confirmed_sites WHERE confirmed_site_id = 'CN-NB-0001' AND country = '中国' AND status = 'CANDIDATE'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM reported_sites WHERE confirmed_site_id = 'CN-NB-0001' AND reported_site_key = 'LIT-SITE-CONFIRMED-S001'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT effective_site_key FROM record_site_bridge",
                String.class)).isEqualTo("reported:LIT-SITE-CONFIRMED-S001");
    }

    @Test
    void rollsBackEntireSnapshotWhenSourceRelationWriteFails() throws Exception {
        User admin = insertUser(1L, "rollback-admin", "admin", false, false, false, true);
        byte[] workbook = validWorkbook("LIT-ROLLBACK");
        DataUploadPreviewResponse preview = service.preview(file(workbook), admin, false);
        completeReview(preview.getBatch().getUploadId(), admin, workbook);
        jdbcTemplate.update("""
                INSERT INTO literatures (literature_code, title, doi)
                VALUES ('OLD-LITERATURE', 'Existing snapshot', '10.1000/old')
                """);
        jdbcTemplate.execute("DROP TABLE icd11_sankey_path_sources");

        assertThatThrownBy(() -> service.sync(preview.getBatch().getUploadId(), admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("工作簿同步失败");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM literatures", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM literatures WHERE literature_code = 'OLD-LITERATURE'",
                Integer.class
        )).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM measurements", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM icd11_sankey_paths", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM data_upload_batches WHERE upload_id = ?",
                String.class,
                preview.getBatch().getUploadId()
        )).isEqualTo("APPROVED");
        service.recordSyncFailure(preview.getBatch().getUploadId(), admin, "来源关系写入失败");
        assertThat(jdbcTemplate.queryForMap("""
                SELECT status, sync_error_message
                FROM data_upload_batches
                WHERE upload_id = ?
                """, preview.getBatch().getUploadId()))
                .containsEntry("status", "SYNC_FAILED")
                .containsEntry("sync_error_message", "来源关系写入失败");
    }

    @Test
    void removesOnlyNewFileWhenUploadDatabaseTransactionRollsBack() throws Exception {
        User uploader = insertUser(1L, "upload-rollback", "viewer", true, false, false, true);
        Files.createDirectories(TEST_UPLOAD_DIR);
        Path historicalFile = TEST_UPLOAD_DIR.resolve("historical-file.xlsx");
        Files.writeString(historicalFile, "history");
        jdbcTemplate.execute("DROP TABLE data_upload_rows");

        assertThatThrownBy(() -> service.preview(file(validWorkbook("LIT-UPLOAD-ROLLBACK")), uploader, false))
                .isInstanceOf(RuntimeException.class);

        assertThat(historicalFile).exists();
        assertThat(countUploadFiles()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM data_upload_batches", Integer.class)).isZero();
        Files.deleteIfExists(historicalFile);
    }

    @Test
    void removesOnlyNewReviewPackageFileWhenPackageTransactionRollsBack() throws Exception {
        User admin = insertUser(1L, "package-rollback", "admin", false, false, false, true);
        byte[] workbook = validWorkbook("LIT-PACKAGE-ROLLBACK");
        Long uploadId = service.preview(file(workbook), admin, false).getBatch().getUploadId();
        service.acceptSourceReview(uploadId, admin, null);
        long filesBeforePackage = countUploadFiles();
        jdbcTemplate.execute("DROP TABLE data_upload_rows");

        assertThatThrownBy(() -> service.uploadReviewPackage(uploadId, file(workbook), admin, false))
                .isInstanceOf(RuntimeException.class);

        assertThat(countUploadFiles()).isEqualTo(filesBeforePackage);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_upload_review_packages",
                Integer.class
        )).isZero();
    }

    @Test
    void reviewerCanRejectOnlyPendingBatch() {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User reviewer = insertUser(2L, "reviewer", "viewer", false, true, false, true);
        User syncer = insertUser(3L, "syncer", "viewer", false, false, true, true);
        Long rejectedId = insertBatch(uploader.getUserId(), "PENDING_REVIEW", null);
        Long approvedId = insertBatch(uploader.getUserId(), "PENDING_REVIEW", null);

        assertThat(service.reject(rejectedId, reviewer, "数据需修正").getStatus()).isEqualTo("REVISION_REQUIRED");
        assertThatThrownBy(() -> service.sync(rejectedId, syncer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不能同步入库");
        assertThatThrownBy(() -> service.approve(approvedId, reviewer, fullyConfirmedDecision()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未完成初审和完整整理包");
        assertThatThrownBy(() -> service.reject(rejectedId, reviewer, "再次退回"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不能退回修改");
    }

    @Test
    void uploadersSeeOnlyTheirOwnBatchesWhileReviewersAndSyncersSeeQueues() {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User anotherUploader = insertUser(2L, "other", "viewer", true, false, false, true);
        User reviewer = insertUser(3L, "reviewer", "viewer", false, true, false, true);
        User syncer = insertUser(4L, "syncer", "viewer", false, false, true, true);
        Long ownPending = insertBatch(uploader.getUserId(), "PENDING_REVIEW", null);
        Long otherPending = insertBatch(anotherUploader.getUserId(), "PENDING_REVIEW", null);
        Long approved = insertBatch(anotherUploader.getUserId(), "APPROVED", null);
        Long syncFailed = insertBatch(anotherUploader.getUserId(), "SYNC_FAILED", null);

        DataUploadBatchPageResponse ownPage = service.listBatches(uploader, 1, 20, null, null, "all", null, null);
        assertThat(ownPage.getItems()).extracting("uploadId").containsExactly(ownPending);

        DataUploadBatchPageResponse reviewQueue = service.listBatches(reviewer, 1, 20, null, null, "pendingReview", null, null);
        assertThat(reviewQueue.getItems()).extracting("uploadId").containsExactlyInAnyOrder(ownPending, otherPending);

        DataUploadBatchPageResponse syncQueue = service.listBatches(syncer, 1, 20, null, null, "approved", null, null);
        assertThat(syncQueue.getItems()).extracting("uploadId").containsExactly(syncFailed, approved);
    }

    @Test
    void downloadPermissionBlocksUsersButAdminKeepsFullCapability() throws Exception {
        User blocked = insertUser(1L, "blocked", "viewer", true, false, false, false);
        User admin = insertUser(2L, "admin", "admin", false, false, false, false);
        Files.createDirectories(TEST_UPLOAD_DIR);
        Path storedFile = TEST_UPLOAD_DIR.resolve("source.xlsx");
        Files.writeString(storedFile, "test");
        Long uploadId = insertBatch(blocked.getUserId(), "PENDING_REVIEW", storedFile.toString());
        Path packageFile = TEST_UPLOAD_DIR.resolve("package.xlsx");
        Files.writeString(packageFile, "review package");
        jdbcTemplate.update("""
                INSERT INTO data_upload_review_packages (
                    upload_id, version_no, file_name, stored_file_path, sha256, uploaded_by, status
                ) VALUES (?, 1, 'package.xlsx', ?, 'package-sha', ?, 'VALID')
                """, uploadId, packageFile.toString(), admin.getUserId());
        Long packageId = jdbcTemplate.queryForObject(
                "SELECT MAX(package_id) FROM data_upload_review_packages", Long.class);

        assertThatThrownBy(() -> service.getStoredFile(uploadId, blocked))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThatThrownBy(() -> service.getReviewPackageFile(uploadId, packageId, blocked))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThat(service.getStoredFile(uploadId, admin)).isEqualTo(storedFile);
        assertThat(service.getReviewPackageFile(uploadId, packageId, admin)).isEqualTo(packageFile);
    }

    @Test
    void downloadRejectsMissingFilesAndPathsOutsideManagedRoots() throws Exception {
        User admin = insertUser(1L, "download-admin", "admin", false, false, false, false);
        Files.createDirectories(TEST_UPLOAD_DIR);
        Path missingFile = TEST_UPLOAD_DIR.resolve("missing.xlsx");
        Long missingId = insertBatch(admin.getUserId(), "PENDING_REVIEW", missingFile.toString());

        assertThatThrownBy(() -> service.getStoredFile(missingId, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原始文件已被移除");

        Path outsideFile = tempDir.resolve("outside.xlsx");
        Files.writeString(outsideFile, "outside");
        Long traversalId = insertBatch(admin.getUserId(), "PENDING_REVIEW", outsideFile.toString());

        assertThatThrownBy(() -> service.getStoredFile(traversalId, admin))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原始文件路径无效");
        assertThat(outsideFile).exists();
    }

    @Test
    void concurrentSyncAllowsOnlyOneSuccessfulTransition() throws Exception {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User syncer = insertUser(2L, "syncer", "viewer", false, false, true, true);
        Long uploadId = insertBatch(uploader.getUserId(), "APPROVED", null);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        service.sync(uploadId, syncer);
                        return true;
                    } catch (BusinessException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long successes = 0;
            for (Future<Boolean> future : futures) {
                if (future.get()) {
                    successes += 1;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM data_upload_batches WHERE upload_id = ?",
                    String.class,
                    uploadId
            )).isEqualTo("SYNCED");
        } finally {
            executor.shutdownNow();
        }
    }

    private User insertUser(Long id,
                            String username,
                            String role,
                            boolean canUpload,
                            boolean canReview,
                            boolean canSync,
                            boolean canDownload) {
        jdbcTemplate.update("""
                        INSERT INTO users (
                            user_id, username, email, role,
                            can_upload, can_review_uploads, can_sync_data, can_download
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                id,
                username,
                username + "@example.test",
                role,
                canUpload,
                canReview,
                canSync,
                canDownload
        );
        User user = new User();
        user.setUserId(id);
        user.setUsername(username);
        user.setEmail(username + "@example.test");
        user.setRole(role);
        user.setCanUpload(canUpload);
        user.setCanReviewUploads(canReview);
        user.setCanSyncData(canSync);
        user.setCanDownload(canDownload);
        return user;
    }

    private Long insertBatch(Long uploadedBy, String status, String storedFilePath) {
        jdbcTemplate.update("""
                        INSERT INTO data_upload_batches (
                            file_name, stored_file_path, sha256, uploaded_by, status
                        ) VALUES (?, ?, ?, ?, ?)
                        """,
                "batch-" + System.nanoTime() + ".xlsx",
                storedFilePath,
                "sha-" + System.nanoTime(),
                uploadedBy,
                status
        );
        return jdbcTemplate.queryForObject("SELECT MAX(upload_id) FROM data_upload_batches", Long.class);
    }

    private MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile(
                "file",
                "wbe-upload.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                bytes
        );
    }

    private long countUploadFiles() throws Exception {
        if (!Files.isDirectory(TEST_UPLOAD_DIR)) {
            return 0;
        }
        try (java.util.stream.Stream<Path> files = Files.list(TEST_UPLOAD_DIR)) {
            return files.filter(Files::isRegularFile).count();
        }
    }

    private void assertWorkbookRowsRoundTrip(Path workbookPath, Long uploadId) throws Exception {
        List<StoredUploadRow> storedRows = jdbcTemplate.query("""
                        SELECT sheet_name, excel_row_number, raw_json
                        FROM data_upload_rows
                        WHERE upload_id = ? AND row_stage = 'SUBMISSION'
                        ORDER BY sheet_name, excel_row_number
                        """,
                (rs, rowNum) -> new StoredUploadRow(rs.getString(1), rs.getInt(2), rs.getString(3)),
                uploadId
        );
        ObjectMapper mapper = new ObjectMapper();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);

        try (Workbook workbook = WorkbookFactory.create(workbookPath.toFile())) {
            Map<String, List<String>> headersBySheet = new LinkedHashMap<>();
            for (String sheetName : List.of("数据表")) {
                Row headerRow = workbook.getSheet(sheetName).getRow(0);
                List<String> headers = new ArrayList<>();
                for (int index = 0; index < headerRow.getLastCellNum(); index++) {
                    String header = formatter.formatCellValue(headerRow.getCell(index));
                    if (header != null && !header.isBlank()) headers.add(header);
                }
                headersBySheet.put(sheetName, headers);
            }
            for (StoredUploadRow stored : storedRows) {
                List<String> headers = headersBySheet.get(stored.sheetName());
                Row sourceRow = workbook.getSheet(stored.sheetName()).getRow(stored.excelRowNumber() - 1);
                Map<String, String> expected = new LinkedHashMap<>();
                for (int index = 0; index < headers.size(); index++) {
                    String value = sourceRow == null ? "" : formatter.formatCellValue(sourceRow.getCell(index));
                    expected.put(headers.get(index), value == null ? "" : value);
                }
                Map<String, String> actual = mapper.readValue(
                        stored.rawJson(),
                        new TypeReference<LinkedHashMap<String, String>>() { }
                );
                assertThat(hashRow(actual, headers))
                        .as(stored.sheetName() + "!" + stored.excelRowNumber())
                        .isEqualTo(hashRow(expected, headers));
            }
        }
    }

    private String hashRow(Map<String, String> values, List<String> headers) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (String header : headers) {
            digest.update(header.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(values.getOrDefault(header, "").getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0xff);
        }
        return java.util.HexFormat.of().formatHex(digest.digest());
    }

    private byte[] withoutCoreProperties(byte[] workbookBytes) throws Exception {
        try (InputStream source = new ByteArrayInputStream(workbookBytes);
             ZipInputStream input = new ZipInputStream(source);
             ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ZipOutputStream output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                if ("docProps/core.xml".equals(entry.getName())) {
                    continue;
                }
                byte[] content = input.readAllBytes();
                if ("[Content_Types].xml".equals(entry.getName())) {
                    content = new String(content, StandardCharsets.UTF_8)
                            .replaceAll("<Override[^>]*PartName=\"/docProps/core\\\\.xml\"[^>]*/>", "")
                            .getBytes(StandardCharsets.UTF_8);
                } else if ("_rels/.rels".equals(entry.getName())) {
                    content = new String(content, StandardCharsets.UTF_8)
                            .replaceAll("<Relationship[^>]*core-properties[^>]*/>", "")
                            .getBytes(StandardCharsets.UTF_8);
                }
                output.putNextEntry(new ZipEntry(entry.getName()));
                output.write(content);
                output.closeEntry();
            }
            output.finish();
            return bytes.toByteArray();
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)
        );
    }

    private void completeReview(Long uploadId, User reviewer, byte[] reviewPackage) {
        DataUploadSourceReviewRequest sourceReview = new DataUploadSourceReviewRequest();
        sourceReview.setNote("原始提交可进入整理阶段");
        assertThat(service.acceptSourceReview(uploadId, reviewer, sourceReview).getStatus())
                .isEqualTo("ENRICHMENT_REQUIRED");
        DataUploadReviewPackageResponse uploaded =
                service.uploadReviewPackage(uploadId, file(reviewPackage), reviewer, false);
        assertThat(uploaded.getStatus()).isEqualTo("VALID");
        assertThat(service.approve(uploadId, reviewer, fullyConfirmedDecision()).getStatus())
                .isEqualTo("APPROVED");
    }

    private DataUploadReviewDecisionRequest fullyConfirmedDecision() {
        DataUploadReviewDecisionRequest request = new DataUploadReviewDecisionRequest();
        request.setSourceCoverageConfirmed(true);
        request.setTraceabilityConfirmed(true);
        request.setValuesAndUnitsConfirmed(true);
        request.setSiteLinkageConfirmed(true);
        request.setIcd11Confirmed(true);
        request.setMethodologyConfirmed(true);
        request.setCoreMarkerConfirmed(true);
        request.setProductionDiffConfirmed(true);
        request.setNote("集成测试终审");
        return request;
    }

    private byte[] validWorkbook(String literatureCode) throws Exception {
        byte[] template = service.createReviewPackageTemplate();
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(template));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet("数据表");
            Row row = sheet.createRow(1);
            Map<String, String> values = new LinkedHashMap<>();
            values.put("文献编号", literatureCode);
            values.put("目标类别", "药物");
            values.put("目标物质类别", "抗生素");
            values.put("目标物质子类", "大环内酯");
            values.put("药物", "阿奇霉素");
            values.put("采样方法", "24h composite");
            values.put("分析方法", "LC-MS/MS");
            values.put("MDL_value", "0.1");
            values.put("MDL_unit", "ng/L");
            values.put("污水厂名称", "测试污水厂");
            values.put("污水厂位置_国", "中国");
            values.put("污水厂位置_省", "浙江省");
            values.put("污水厂位置_市", "宁波市");
            values.put("样品采集时间", "2025-06-15 10:30");
            values.put("采样开始时间_YYYY_MM", "2025-06");
            values.put("采样结束时间_YYYY_MM", "2025-06");
            values.put("做图浓度_value", "12.5");
            values.put("做图浓度_unit", "ng/L");
            values.put("DOI", "10.1000/integration-test");
            values.put("来源工作簿说明", "integration-test.xlsx");
            values.put("原表行号说明", "2");

            List<String> headers = new ArrayList<>(DataUploadService.REQUIRED_HEADERS);
            headers.addAll(DataUploadService.OPTIONAL_HEADERS);
            for (int i = 0; i < headers.size(); i++) {
                row.createCell(i).setCellValue(values.getOrDefault(headers.get(i), ""));
            }

            Sheet literatureSheet = workbook.getSheet("文献基础信息");
            Row literatureRow = literatureSheet.createRow(1);
            Map<String, String> literatureValues = Map.of(
                    "文献编号", literatureCode,
                    "文献名", "Integration test literature",
                    "DOI", "10.1000/integration-test",
                    "keywords", "WBE",
                    "abstract", "Integration test abstract"
            );
            for (int i = 0; i < DataUploadService.LITERATURE_HEADERS.size(); i++) {
                String header = DataUploadService.LITERATURE_HEADERS.get(i);
                literatureRow.createCell(i).setCellValue(literatureValues.getOrDefault(header, ""));
            }

            Sheet siteSheet = workbook.getSheet("点位关联表");
            Row siteRow = siteSheet.createRow(1);
            Map<String, String> siteValues = new LinkedHashMap<>();
            siteValues.put("文献编号", literatureCode);
            siteValues.put("DOI", "10.1000/integration-test");
            siteValues.put("国家", "中国");
            siteValues.put("省/州", "浙江省");
            siteValues.put("市", "宁波市");
            siteValues.put("原始污水厂名称", "测试污水厂");
            siteValues.put("规范污水厂名称", "测试污水厂");
            siteValues.put("reported_site_key", literatureCode + "-S001");
            siteValues.put("是否计入点位数", "是");
            for (int i = 0; i < DataUploadService.SITE_HEADERS.size(); i++) {
                String header = DataUploadService.SITE_HEADERS.get(i);
                siteRow.createCell(i).setCellValue(siteValues.getOrDefault(header, ""));
            }

            Sheet mappingSheet = workbook.getSheet("药物疾病ICD11映射");
            Row mappingRow = mappingSheet.createRow(1);
            Map<String, String> mappingValues = new LinkedHashMap<>();
            mappingValues.put("目标类别", "药物类");
            mappingValues.put("目标物质类别", "抗生素");
            mappingValues.put("目标物质子类", "大环内酯");
            mappingValues.put("药物", "阿奇霉素");
            mappingValues.put("生物标记物名称", "阿奇霉素");
            mappingValues.put("ICD11_Level1_Code", "01");
            mappingValues.put("ICD11_Level1_Name", "某疾病大类");
            mappingValues.put("ICD11_Level2_Code", "01A");
            mappingValues.put("ICD11_Level2_Name", "某疾病二级类");
            mappingValues.put("映射层级", "Level2");
            mappingValues.put("匹配类型", "人工复核");
            mappingValues.put("是否进入桑基图", "是");
            mappingValues.put("复核状态", "已确认映射");
            mappingValues.put("涉及文献数", "1");
            mappingValues.put("数据行数", "1");
            mappingValues.put("涉及文献编号", literatureCode);
            mappingValues.put("涉及DOI", "10.1000/integration-test");
            mappingValues.put("唯一DOI数", "1");
            mappingValues.put("DOI缺失数", "0");
            for (int i = 0; i < DataUploadService.ICD11_HEADERS.size(); i++) {
                String header = DataUploadService.ICD11_HEADERS.get(i);
                mappingRow.createCell(i).setCellValue(mappingValues.getOrDefault(header, ""));
            }

            Sheet methodologySheet = workbook.getSheet("采样方法统一审计");
            Row methodologyRow = methodologySheet.createRow(1);
            Map<String, String> methodologyValues = new LinkedHashMap<>();
            methodologyValues.put("文献编号", literatureCode);
            methodologyValues.put("数据表采样方法（原文摘录）", "24h composite");
            methodologyValues.put("规范化采样方法明细", "24 h时间比例复合进水样");
            methodologyValues.put("标准采样方法", "时间比例复合采样");
            methodologyValues.put("采样主类", "复合采样");
            methodologyValues.put("采样对象", "进水样");
            methodologyValues.put("比例方式", "时间比例");
            methodologyValues.put("采样/部署时长", "24 h");
            methodologyValues.put("被动采样器类型", "不适用");
            methodologyValues.put("站点对应状态", "已明确或不涉及");
            methodologyValues.put("来源文件", "integration-test.pdf");
            methodologyValues.put("页码/表号/sheet", "PDF第2页");
            methodologyValues.put("原文证据", "24 h composite sample");
            methodologyValues.put("影响行数", "1");
            methodologyValues.put("复核状态", "已复核");
            methodologyValues.put("标准化处理说明", "人工复核");
            for (int i = 0; i < DataUploadService.METHODOLOGY_AUDIT_HEADERS.size(); i++) {
                String header = DataUploadService.METHODOLOGY_AUDIT_HEADERS.get(i);
                methodologyRow.createCell(i).setCellValue(methodologyValues.getOrDefault(header, ""));
            }

            Sheet coreSheet = workbook.getSheet("核心标记物优先级识别");
            Row coreRow = coreSheet.createRow(1);
            Map<String, String> coreValues = new LinkedHashMap<>();
            coreValues.put("序号", "1");
            coreValues.put("目标类别_主类", "药物");
            coreValues.put("目标物质类别_主类", "抗生素");
            coreValues.put("目标物质子类_主类", "大环内酯");
            coreValues.put("代表目标物质/药物", "阿奇霉素");
            coreValues.put("生物标记物名称", "阿奇霉素");
            coreValues.put("biomarker", "阿奇霉素");
            coreValues.put("原始记录数", "1");
            coreValues.put("文献编号数", "1");
            coreValues.put("独立DOI数", "1");
            coreValues.put("目标类别样本量", "1");
            coreValues.put("目标类别内综合得分", "80");
            coreValues.put("目标类别内排名", "1");
            coreValues.put("目标物质类别样本量", "1");
            coreValues.put("目标物质类别内综合得分", "80");
            coreValues.put("目标物质类别内排名", "1");
            coreValues.put("目标物质子类样本量", "1");
            coreValues.put("目标物质子类内综合得分", "80");
            coreValues.put("目标物质子类内排名", "1");
            coreValues.put("三级评价状态", "仅排名");
            coreValues.put("四级评价状态", "不适用");
            for (int i = 0; i < DataUploadService.CORE_MARKER_HEADERS.size(); i++) {
                String header = DataUploadService.CORE_MARKER_HEADERS.get(i);
                coreRow.createCell(i).setCellValue(coreValues.getOrDefault(header, ""));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithDataFields(String literatureCode, Map<String, String> additions) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(validWorkbook(literatureCode)));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.getSheet("数据表");
            Row row = sheet.getRow(1);
            List<String> headers = new ArrayList<>(DataUploadService.REQUIRED_HEADERS);
            headers.addAll(DataUploadService.OPTIONAL_HEADERS);
            for (Map.Entry<String, String> entry : additions.entrySet()) {
                int index = headers.indexOf(entry.getKey());
                if (index < 0) throw new IllegalArgumentException("Unknown upload field: " + entry.getKey());
                row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(entry.getValue());
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private byte[] workbookWithSiteFields(String literatureCode, Map<String, String> additions) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(validWorkbook(literatureCode)));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Row row = workbook.getSheet("点位关联表").getRow(1);
            for (Map.Entry<String, String> entry : additions.entrySet()) {
                int index = DataUploadService.SITE_HEADERS.indexOf(entry.getKey());
                if (index < 0) throw new IllegalArgumentException("Unknown site field: " + entry.getKey());
                row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).setCellValue(entry.getValue());
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:data-upload-tests;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                    "sa",
                    ""
            );
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        DataUploadStorageProperties dataUploadStorageProperties() {
            DataUploadStorageProperties properties = new DataUploadStorageProperties();
            properties.setUploadDir(TEST_UPLOAD_DIR);
            return properties;
        }

        @Bean
        DataUploadService dataUploadService(JdbcTemplate jdbcTemplate,
                                            ObjectMapper objectMapper,
                                            DataUploadStorageProperties storageProperties) {
            return new DataUploadService(jdbcTemplate, objectMapper, mock(DataSource.class), storageProperties);
        }
    }

    private record StoredUploadRow(String sheetName, int excelRowNumber, String rawJson) {
    }
}
