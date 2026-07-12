package com.licong.webbackup.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.licong.webbackup.dto.upload.DataUploadBatchPageResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
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

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

@SpringJUnitConfig(DataUploadServiceIntegrationTest.TestConfig.class)
class DataUploadServiceIntegrationTest {

    private final DataUploadService service;
    private final JdbcTemplate jdbcTemplate;

    @TempDir
    Path tempDir;

    @Autowired
    DataUploadServiceIntegrationTest(DataUploadService service, JdbcTemplate jdbcTemplate) {
        this.service = service;
        this.jdbcTemplate = jdbcTemplate;
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
                    synced_by BIGINT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE data_upload_rows (
                    row_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    upload_id BIGINT NOT NULL,
                    sheet_name VARCHAR(120) NOT NULL,
                    excel_row_number INT NOT NULL,
                    row_status VARCHAR(32) NOT NULL,
                    raw_json CLOB NOT NULL,
                    error_json CLOB,
                    warning_json CLOB,
                    synced_measurement_id BIGINT NULL,
                    synced_entity_type VARCHAR(40) NULL,
                    synced_entity_id BIGINT NULL,
                    created_at TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE compounds (
                    compound_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    target_category VARCHAR(160) NOT NULL,
                    substance_category VARCHAR(180) NOT NULL,
                    substance_subclass VARCHAR(180),
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
                CREATE TABLE sampling_events (
                    event_id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    plant_id BIGINT NOT NULL,
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
                "SELECT stored_file_path FROM data_upload_batches WHERE stored_file_path IS NOT NULL",
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

        assertThat(preview.getBatch().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(preview.getBatch().getErrorRows()).isZero();
        assertThatThrownBy(() -> service.preview(file(workbook), uploader, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("相同 SHA256");
        assertThatThrownBy(() -> service.preview(file(workbook), uploader, true))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        DataUploadPreviewResponse adminOverride = service.preview(file(workbook), admin, true);
        assertThat(adminOverride.getBatch().getStatus()).isEqualTo("PENDING_REVIEW");
        assertThat(adminOverride.getBatch().getDuplicateMessage()).contains("相同 SHA256");
        assertThat(service.approve(adminOverride.getBatch().getUploadId(), admin).getStatus()).isEqualTo("APPROVED");
        assertThat(service.sync(adminOverride.getBatch().getUploadId(), admin).getBatch().getStatus()).isEqualTo("SYNCED");
    }

    @Test
    void previewsCompletePublishedWorkbookWithoutLosingRows() throws Exception {
        Path workbookPath = Path.of("..", "WBE汇总表6.29.xlsx").toAbsolutePath().normalize();
        org.junit.jupiter.api.Assumptions.assumeTrue(Files.exists(workbookPath));
        User uploader = insertUser(1L, "publisher", "viewer", true, false, false, true);
        User admin = insertUser(2L, "acceptance-admin", "admin", false, false, false, true);
        MockMultipartFile workbookFile = new MockMultipartFile(
                "file",
                workbookPath.getFileName().toString(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                Files.readAllBytes(workbookPath)
        );

        DataUploadPreviewResponse result = service.preview(workbookFile, uploader, false);

        assertThat(result.getHeaderErrors()).isEmpty();
        assertThat(result.getBatch().getTotalRows()).isEqualTo(22_687 + 778 + 198);
        assertThat(result.getBatch().getErrorRows()).isZero();
        assertThat(result.getSheetSummaries())
                .extracting("sheetName", "totalRows")
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("数据表", 22_687),
                        org.assertj.core.groups.Tuple.tuple("药物疾病ICD11映射", 778),
                        org.assertj.core.groups.Tuple.tuple("文献基础信息", 198)
                );
        Map<String, Long> mappingDepthCounts = jdbcTemplate.query("""
                        SELECT raw_json
                        FROM data_upload_rows
                        WHERE upload_id = ? AND sheet_name = '药物疾病ICD11映射'
                        """,
                (rs, rowNum) -> rs.getString(1).contains("\"映射层级\":\"Level3\"") ? "Level3" : "Level2",
                result.getBatch().getUploadId()
        ).stream().collect(java.util.stream.Collectors.groupingBy(
                value -> value,
                java.util.stream.Collectors.counting()
        ));
        assertThat(mappingDepthCounts).containsEntry("Level3", 502L).containsEntry("Level2", 276L);
        assertWorkbookRowsRoundTrip(workbookPath, result.getBatch().getUploadId());

        service.approve(result.getBatch().getUploadId(), admin);
        DataUploadSyncResponse syncResult = service.sync(result.getBatch().getUploadId(), admin);

        assertThat(syncResult.getInsertedRowsBySheet())
                .containsEntry("数据表", 22_687)
                .containsEntry("药物疾病ICD11映射", 778)
                .containsEntry("文献基础信息", 198);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM measurements", Integer.class)).isEqualTo(22_687);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM icd11_sankey_paths", Integer.class)).isEqualTo(778);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM literatures", Integer.class)).isEqualTo(198);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM icd11_sankey_paths
                WHERE mapping_level = 'Level2' AND icd11_level3_name IS NULL
                """, Integer.class)).isEqualTo(276);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM icd11_sankey_paths
                WHERE mapping_level = 'Level3' AND icd11_level3_name IS NOT NULL
                """, Integer.class)).isEqualTo(502);
    }

    @Test
    void reviewAndSyncPermissionsEnforceSeparatedWorkflowAndPersistTraceFields() throws Exception {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User reviewer = insertUser(2L, "reviewer", "viewer", false, true, false, true);
        User syncer = insertUser(3L, "syncer", "viewer", false, false, true, true);
        DataUploadPreviewResponse preview = service.preview(file(validWorkbook("LIT-TRACE")), uploader, false);
        Long uploadId = preview.getBatch().getUploadId();

        assertThatThrownBy(() -> service.approve(uploadId, syncer))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThatThrownBy(() -> service.sync(uploadId, syncer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未审核通过");

        assertThat(service.approve(uploadId, reviewer).getStatus()).isEqualTo("APPROVED");
        assertThatThrownBy(() -> service.sync(uploadId, reviewer))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);

        DataUploadSyncResponse result = service.sync(uploadId, syncer);

        assertThat(result.getBatch().getStatus()).isEqualTo("SYNCED");
        assertThat(result.getInsertedRows()).isEqualTo(3);
        assertThat(result.getInsertedRowsBySheet()).containsEntry("数据表", 1)
                .containsEntry("药物疾病ICD11映射", 1)
                .containsEntry("文献基础信息", 1);
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
        Map<String, Object> rowState = jdbcTemplate.queryForMap(
                """
                SELECT row_status, synced_measurement_id
                FROM data_upload_rows
                WHERE upload_id = ? AND sheet_name = '数据表'
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
    void rollsBackEntireSnapshotWhenSourceRelationWriteFails() throws Exception {
        User admin = insertUser(1L, "rollback-admin", "admin", false, false, false, true);
        DataUploadPreviewResponse preview = service.preview(file(validWorkbook("LIT-ROLLBACK")), admin, false);
        service.approve(preview.getBatch().getUploadId(), admin);
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
    }

    @Test
    void reviewerCanRejectOnlyPendingBatch() {
        User uploader = insertUser(1L, "uploader", "viewer", true, false, false, true);
        User reviewer = insertUser(2L, "reviewer", "viewer", false, true, false, true);
        User syncer = insertUser(3L, "syncer", "viewer", false, false, true, true);
        Long rejectedId = insertBatch(uploader.getUserId(), "PENDING_REVIEW", null);
        Long approvedId = insertBatch(uploader.getUserId(), "PENDING_REVIEW", null);

        assertThat(service.reject(rejectedId, reviewer, "数据需修正").getStatus()).isEqualTo("REJECTED");
        assertThatThrownBy(() -> service.sync(rejectedId, syncer))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("当前状态不能同步入库");
        assertThat(service.approve(approvedId, reviewer).getStatus()).isEqualTo("APPROVED");
        assertThatThrownBy(() -> service.reject(approvedId, reviewer, "撤回"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核通过批次不能驳回");
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

        DataUploadBatchPageResponse ownPage = service.listBatches(uploader, 1, 20, null, null, "all", null, null);
        assertThat(ownPage.getItems()).extracting("uploadId").containsExactly(ownPending);

        DataUploadBatchPageResponse reviewQueue = service.listBatches(reviewer, 1, 20, null, null, "pendingReview", null, null);
        assertThat(reviewQueue.getItems()).extracting("uploadId").containsExactlyInAnyOrder(ownPending, otherPending);

        DataUploadBatchPageResponse syncQueue = service.listBatches(syncer, 1, 20, null, null, "approved", null, null);
        assertThat(syncQueue.getItems()).extracting("uploadId").containsExactly(approved);
    }

    @Test
    void downloadPermissionBlocksUsersButAdminKeepsFullCapability() throws Exception {
        User blocked = insertUser(1L, "blocked", "viewer", true, false, false, false);
        User admin = insertUser(2L, "admin", "admin", false, false, false, false);
        Path storedFile = tempDir.resolve("source.xlsx");
        Files.writeString(storedFile, "test");
        Long uploadId = insertBatch(blocked.getUserId(), "PENDING_REVIEW", storedFile.toString());

        assertThatThrownBy(() -> service.getStoredFile(uploadId, blocked))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(403);
        assertThat(service.getStoredFile(uploadId, admin)).isEqualTo(storedFile);
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

    private void assertWorkbookRowsRoundTrip(Path workbookPath, Long uploadId) throws Exception {
        List<StoredUploadRow> storedRows = jdbcTemplate.query("""
                        SELECT sheet_name, excel_row_number, raw_json
                        FROM data_upload_rows
                        WHERE upload_id = ?
                        ORDER BY sheet_name, excel_row_number
                        """,
                (rs, rowNum) -> new StoredUploadRow(rs.getString(1), rs.getInt(2), rs.getString(3)),
                uploadId
        );
        Map<String, List<String>> headersBySheet = new LinkedHashMap<>();
        List<String> dataHeaders = new ArrayList<>(DataUploadService.REQUIRED_HEADERS);
        dataHeaders.addAll(DataUploadService.OPTIONAL_HEADERS);
        headersBySheet.put("数据表", dataHeaders);
        headersBySheet.put("药物疾病ICD11映射", DataUploadService.ICD11_HEADERS);
        headersBySheet.put("文献基础信息", DataUploadService.LITERATURE_HEADERS);
        ObjectMapper mapper = new ObjectMapper();
        DataFormatter formatter = new DataFormatter(Locale.CHINA);

        try (Workbook workbook = WorkbookFactory.create(workbookPath.toFile())) {
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

    private byte[] validWorkbook(String literatureCode) throws Exception {
        byte[] template = service.createTemplateWorkbook();
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
        DataUploadService dataUploadService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
            return new DataUploadService(jdbcTemplate, objectMapper, mock(DataSource.class));
        }
    }

    private record StoredUploadRow(String sheetName, int excelRowNumber, String rawJson) {
    }
}
