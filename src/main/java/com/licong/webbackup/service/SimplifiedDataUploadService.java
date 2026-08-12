package com.licong.webbackup.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.config.DataUploadStorageProperties;
import com.licong.webbackup.dto.upload.DataUploadBatchResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadReviewPackageResponse;
import com.licong.webbackup.dto.upload.DataUploadRowResponse;
import com.licong.webbackup.dto.upload.DataUploadSheetSummaryResponse;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.exception.WorkflowStateException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import static com.licong.webbackup.service.SimplifiedUploadWorkbook.ICD11_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.ICD11_SHEET;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.LITERATURE_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.LITERATURE_SHEET;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.METHOD_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.METHOD_SHEET;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.NORMALIZED_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.NORMALIZED_SHEET;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.REVIEW_SHEETS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.SITE_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.SITE_SHEET;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.SUBMISSION_HEADERS;
import static com.licong.webbackup.service.SimplifiedUploadWorkbook.SUBMISSION_SHEET;

@Service
public class SimplifiedDataUploadService {

    public static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    public static final String STATUS_REVISION_REQUIRED = "REVISION_REQUIRED";
    public static final String STATUS_READY_TO_PUBLISH = "READY_TO_PUBLISH";
    public static final String STATUS_PUBLISHING = "PUBLISHING";
    public static final String STATUS_PUBLISHED = "PUBLISHED";
    public static final String STATUS_PUBLISH_FAILED = "PUBLISH_FAILED";

    private static final Logger LOGGER = LoggerFactory.getLogger(SimplifiedDataUploadService.class);
    private static final int PREVIEW_LIMIT = 20;
    private static final Set<String> CORRECTED_FIELDS = Set.of(
            "标准生物标记物名称", "标准biomarker英文", "标准CAS", "标准数值", "标准单位",
            "采样开始年月", "采样结束年月"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataUploadStorageProperties storageProperties;
    private final DataUploadService legacyService;
    private final DataSource dataSource;

    public SimplifiedDataUploadService(JdbcTemplate jdbcTemplate,
                                       ObjectMapper objectMapper,
                                       DataUploadStorageProperties storageProperties,
                                       DataUploadService legacyService,
                                       DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.storageProperties = storageProperties;
        this.legacyService = legacyService;
        this.dataSource = dataSource;
    }

    public byte[] createSubmissionTemplate() {
        return SimplifiedUploadWorkbook.createSubmissionTemplate();
    }

    @Transactional
    public DataUploadPreviewResponse createSubmission(MultipartFile file, User user) {
        legacyService.requireCanUpload(user);
        StoredUpload source = storeMultipart(file, "submission-source");
        SimplifiedUploadWorkbook.ParsedSubmission parsed = SimplifiedUploadWorkbook.parseSubmission(source.path(), null);
        Path normalizedPath = storeBytes(SimplifiedUploadWorkbook.createNormalizedSubmissionWorkbook(parsed.rows()), "submission");
        int errorRows = (int) parsed.rows().stream().filter(row -> !row.valid()).count();
        int validRows = parsed.rows().size() - errorRows;
        String status = parsed.valid() ? STATUS_PENDING_REVIEW : STATUS_VALIDATION_FAILED;
        List<String> allErrors = flattenedSubmissionErrors(parsed);
        long uploadId = insertAndReturnKey("""
                INSERT INTO data_upload_batches (
                    file_name, stored_file_path, sha256, uploaded_by, status,
                    total_rows, valid_rows, error_rows, warning_rows, duplicate_message, current_revision_no
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, 1)
                """, source.fileName(), normalizedPath.toString(), source.sha256(), user.getUserId(), status,
                parsed.rows().size(), validRows, errorRows, summarizeErrors(allErrors));
        insertRevision(uploadId, 1, source, user.getUserId(), status, parsed.rows().size(), validRows, errorRows, allErrors);
        List<DataUploadRowResponse> previewRows = insertSubmissionRows(uploadId, 1, parsed.rows());
        insertAudit(uploadId, null, "SUBMISSION_CREATED", user.getUserId(), null, status,
                summarizeErrors(allErrors), Map.of("revision", 1));
        return previewResponse(uploadId, user, parsed, previewRows);
    }

    @Transactional
    public DataUploadPreviewResponse createRevision(Long uploadId, MultipartFile file, User user) {
        legacyService.requireCanUpload(user);
        BatchState batch = lockBatch(uploadId);
        if (!Objects.equals(batch.uploadedBy(), user.getUserId()) && !isAdmin(user)) {
            throw new BusinessException(403, "只有投稿人或系统管理员可以提交修订版本");
        }
        if (!Set.of(STATUS_REVISION_REQUIRED, STATUS_VALIDATION_FAILED).contains(batch.status())) {
            throw new WorkflowStateException("投稿修订要求退回或校验失败状态，实际为 " + batch.status());
        }
        Set<String> allowedIds = new LinkedHashSet<>(jdbcTemplate.queryForList("""
                SELECT submission_row_id FROM data_upload_rows
                WHERE upload_id = ? AND row_stage = 'SUBMISSION' AND submission_row_id IS NOT NULL
                """, String.class, uploadId));
        StoredUpload source = storeMultipart(file, "submission-revision-source");
        SimplifiedUploadWorkbook.ParsedSubmission parsed = SimplifiedUploadWorkbook.parseSubmission(source.path(), allowedIds);
        Path normalizedPath = storeBytes(SimplifiedUploadWorkbook.createNormalizedSubmissionWorkbook(parsed.rows()), "submission-revision");
        int version = batch.currentRevisionNo() + 1;
        int errorRows = (int) parsed.rows().stream().filter(row -> !row.valid()).count();
        int validRows = parsed.rows().size() - errorRows;
        String status = parsed.valid() ? STATUS_PENDING_REVIEW : STATUS_VALIDATION_FAILED;
        List<String> allErrors = flattenedSubmissionErrors(parsed);
        insertRevision(uploadId, version, source, user.getUserId(), status, parsed.rows().size(), validRows, errorRows, allErrors);
        List<DataUploadRowResponse> previewRows = insertSubmissionRows(uploadId, version, parsed.rows());
        jdbcTemplate.update("""
                UPDATE data_upload_batches SET file_name=?, stored_file_path=?, sha256=?, status=?,
                    total_rows=?, valid_rows=?, error_rows=?, warning_rows=0, duplicate_message=?,
                    current_revision_no=?, current_package_id=NULL, approved_package_id=NULL,
                    review_checklist_json=NULL, review_diff_json=NULL, sync_error_message=NULL
                WHERE upload_id=?
                """, source.fileName(), normalizedPath.toString(), source.sha256(), status,
                parsed.rows().size(), validRows, errorRows, summarizeErrors(allErrors), version, uploadId);
        insertAudit(uploadId, null, "SUBMISSION_REVISED", user.getUserId(), batch.status(), status,
                summarizeErrors(allErrors), Map.of("revision", version));
        return previewResponse(uploadId, user, parsed, previewRows);
    }

    public byte[] createReviewDraft(Long uploadId, User user) {
        legacyService.requireCanReviewUploads(user);
        BatchState batch = findBatch(uploadId);
        if (!Set.of(STATUS_PENDING_REVIEW, STATUS_READY_TO_PUBLISH, STATUS_PUBLISH_FAILED).contains(batch.status())) {
            throw new WorkflowStateException("当前批次不能生成审核草稿，实际为 " + batch.status());
        }
        List<SimplifiedUploadWorkbook.SubmissionRow> rows = currentSubmissionRows(uploadId, batch.currentRevisionNo());
        return SimplifiedUploadWorkbook.createReviewDraft(rows);
    }

    @Transactional
    public DataUploadReviewPackageResponse uploadReviewPackage(Long uploadId, MultipartFile file, User user) {
        legacyService.requireCanReviewUploads(user);
        BatchState batch = lockBatch(uploadId);
        if (!Set.of(STATUS_PENDING_REVIEW, STATUS_READY_TO_PUBLISH, STATUS_PUBLISH_FAILED).contains(batch.status())) {
            throw new WorkflowStateException("当前状态不能上传审核包，实际为 " + batch.status());
        }
        StoredUpload stored = storeMultipart(file, "review-package");
        SimplifiedUploadWorkbook.ParsedReview parsed = SimplifiedUploadWorkbook.parseReview(stored.path());
        List<SimplifiedUploadWorkbook.SubmissionRow> submissions = currentSubmissionRows(uploadId, batch.currentRevisionNo());
        validateReviewPackage(parsed, submissions);
        List<String> validationErrors = flattenedReviewErrors(parsed);
        int totalRows = parsed.rowsBySheet().values().stream().mapToInt(List::size).sum();
        int errorRows = (int) parsed.rowsBySheet().values().stream().flatMap(Collection::stream).filter(row -> !row.valid()).count();
        int warningRows = 0;
        int validRows = totalRows - errorRows;
        boolean valid = validationErrors.isEmpty();
        int version = nextPackageVersion(uploadId);
        Map<String, Object> impact = buildImpactSummary(parsed);
        long packageId = insertAndReturnKey("""
                INSERT INTO data_upload_review_packages (
                    upload_id, version_no, file_name, stored_file_path, sha256, uploaded_by, status,
                    total_rows, valid_rows, error_rows, warning_rows, validation_message, diff_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, uploadId, version, stored.fileName(), stored.path().toString(), stored.sha256(), user.getUserId(),
                valid ? "VALID" : "INVALID", totalRows, validRows, errorRows, warningRows,
                toJson(validationErrors), toJson(impact));
        insertReviewRows(uploadId, packageId, parsed);
        if (valid) persistFieldChanges(uploadId, packageId, user.getUserId(), parsed, submissions);
        String nextStatus = valid ? STATUS_READY_TO_PUBLISH : STATUS_PENDING_REVIEW;
        jdbcTemplate.update("""
                UPDATE data_upload_batches SET status=?, current_package_id=?, approved_package_id=NULL,
                    reviewed_by=?, reviewed_at=NOW(), review_action=?, review_note=?, review_diff_json=?
                WHERE upload_id=?
                """, nextStatus, packageId, user.getUserId(), valid ? "PACKAGE_VALID" : "PACKAGE_INVALID",
                summarizeErrors(validationErrors), toJson(impact), uploadId);
        insertAudit(uploadId, packageId, valid ? "REVIEW_PACKAGE_VALID" : "REVIEW_PACKAGE_INVALID",
                user.getUserId(), batch.status(), nextStatus, summarizeErrors(validationErrors), impact);
        return reviewPackageResponse(packageId);
    }

    @Transactional
    public DataUploadBatchResponse returnForRevision(Long uploadId, User user, String reason) {
        legacyService.requireCanReviewUploads(user);
        BatchState batch = lockBatch(uploadId);
        if (!Set.of(STATUS_PENDING_REVIEW, STATUS_READY_TO_PUBLISH, STATUS_PUBLISH_FAILED).contains(batch.status())) {
            throw new WorkflowStateException("当前状态不能退回修改，实际为 " + batch.status());
        }
        String note = requiredReason(reason);
        jdbcTemplate.update("""
                UPDATE data_upload_batches SET status=?, reviewed_by=?, reviewed_at=NOW(),
                    review_action=?, review_note=? WHERE upload_id=?
                """, STATUS_REVISION_REQUIRED, user.getUserId(), STATUS_REVISION_REQUIRED, note, uploadId);
        insertAudit(uploadId, batch.currentPackageId(), "REVISION_REQUIRED", user.getUserId(),
                batch.status(), STATUS_REVISION_REQUIRED, note, null);
        return legacyService.getBatch(uploadId, user);
    }

    @Transactional
    public DataUploadSyncResponse publish(Long uploadId, User user) {
        legacyService.requireCanReviewUploads(user);
        BatchState batch = lockBatch(uploadId);
        if (STATUS_PUBLISHED.equals(batch.status()) && batch.publishedReleaseId() != null) {
            return publishedResponse(uploadId, user, batch.publishedReleaseId());
        }
        if (!Set.of(STATUS_READY_TO_PUBLISH, STATUS_PUBLISH_FAILED).contains(batch.status())) {
            throw new WorkflowStateException("确认入库要求待发布状态，实际为 " + batch.status());
        }
        if (batch.currentPackageId() == null) throw new BusinessException("当前批次没有校验通过的审核包");
        String packageStatus = jdbcTemplate.queryForObject(
                "SELECT status FROM data_upload_review_packages WHERE package_id=? AND upload_id=?",
                String.class, batch.currentPackageId(), uploadId);
        if (!"VALID".equals(packageStatus)) throw new BusinessException("当前审核包未通过校验");

        Long releaseId = existingReleaseId(uploadId);
        if (releaseId == null) {
            releaseId = insertAndReturnKey("""
                    INSERT INTO dataset_releases (upload_id, package_id, status, published_by)
                    VALUES (?, ?, ?, ?)
                    """, uploadId, batch.currentPackageId(), STATUS_PUBLISHING, user.getUserId());
        } else {
            jdbcTemplate.update("""
                    UPDATE dataset_releases SET package_id=?, status=?, published_by=?, error_message=NULL
                    WHERE release_id=?
                    """, batch.currentPackageId(), STATUS_PUBLISHING, user.getUserId(), releaseId);
        }
        jdbcTemplate.update("UPDATE data_upload_batches SET status=?, sync_error_message=NULL WHERE upload_id=?",
                STATUS_PUBLISHING, uploadId);

        List<PackageRow> packageRows = packageRows(batch.currentPackageId());
        Map<String, List<PackageRow>> bySheet = packageRows.stream().collect(Collectors.groupingBy(
                PackageRow::sheetName, LinkedHashMap::new, Collectors.toList()));
        Map<String, String> literatureCodes = publishLiteratures(uploadId, releaseId,
                bySheet.getOrDefault(LITERATURE_SHEET, List.of()));
        Map<String, Map<String, String>> methodByHash = bySheet.getOrDefault(METHOD_SHEET, List.of()).stream()
                .collect(Collectors.toMap(row -> value(row.data(), "原始方法哈希"), PackageRow::data, (first, ignored) -> first));
        Map<String, List<PackageRow>> sitesByGroup = bySheet.getOrDefault(SITE_SHEET, List.of()).stream()
                .filter(row -> "通过".equals(value(row.data(), "审核结论")))
                .collect(Collectors.groupingBy(row -> value(row.data(), "记录组ID"), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<PackageRow>> dataGroups = bySheet.getOrDefault(NORMALIZED_SHEET, List.of()).stream()
                .filter(row -> "发布".equals(value(row.data(), "记录处置")))
                .collect(Collectors.groupingBy(row -> value(row.data(), "记录组ID"), LinkedHashMap::new, Collectors.toList()));

        int inserted = 0;
        int skipped = 0;
        Map<String, Integer> insertedBySheet = new LinkedHashMap<>();
        for (Map.Entry<String, List<PackageRow>> entry : dataGroups.entrySet()) {
            List<PackageRow> group = entry.getValue();
            Map<String, String> wide = buildWideRecord(group, literatureCodes, methodByHash,
                    sitesByGroup.getOrDefault(entry.getKey(), List.of()));
            String recordKey = stableRecordKey(wide);
            Long existing = queryOptionalLong("SELECT measurement_id FROM measurements WHERE record_key=? OR dedupe_key=? LIMIT 1", recordKey, recordKey);
            if (existing != null) {
                skipped++;
                markGroupPublished(group, existing);
                continue;
            }
            long measurementId = insertPublishedMeasurement(uploadId, releaseId, group.getFirst().rowId(), recordKey, wide);
            publishSites(uploadId, measurementId, entry.getKey(),
                    sitesByGroup.getOrDefault(entry.getKey(), List.of()), wide);
            insertHomeTarget(measurementId, uploadId, wide);
            markGroupPublished(group, measurementId);
            inserted++;
        }
        insertedBySheet.put(NORMALIZED_SHEET, inserted);
        int icdInserted = publishIcd11(uploadId, releaseId, bySheet.getOrDefault(ICD11_SHEET, List.of()), literatureCodes,
                bySheet.getOrDefault(NORMALIZED_SHEET, List.of()));
        insertedBySheet.put(ICD11_SHEET, icdInserted);
        insertedBySheet.put(LITERATURE_SHEET, literatureCodes.size());
        insertedBySheet.put(SITE_SHEET, bySheet.getOrDefault(SITE_SHEET, List.of()).size());
        insertedBySheet.put(METHOD_SHEET, bySheet.getOrDefault(METHOD_SHEET, List.of()).size());

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("releaseId", releaseId);
        manifest.put("uploadId", uploadId);
        manifest.put("packageId", batch.currentPackageId());
        manifest.put("insertedRecordGroups", inserted);
        manifest.put("skippedDuplicates", skipped);
        manifest.put("existingRowsDeleted", 0);
        manifest.put("publishedAt", LocalDateTime.now().toString());
        refreshDerivedReadModels(manifest);

        jdbcTemplate.update("""
                UPDATE dataset_releases SET status=?, inserted_records=?, skipped_records=?, manifest_json=?,
                    error_message=NULL, published_at=NOW() WHERE release_id=?
                """, STATUS_PUBLISHED, inserted, skipped, toJson(manifest), releaseId);
        jdbcTemplate.update("""
                UPDATE data_upload_batches SET status=?, approved_package_id=current_package_id,
                    reviewed_by=?, reviewed_at=NOW(), review_action='PUBLISHED',
                    review_checklist_json=?, published_release_id=?, synced_rows=?, synced_by=?, synced_at=NOW(),
                    sync_error_message=NULL WHERE upload_id=?
                """, STATUS_PUBLISHED, user.getUserId(), toJson(Map.of("singlePublishConfirmation", true)),
                releaseId, inserted, user.getUserId(), uploadId);
        insertAudit(uploadId, batch.currentPackageId(), "PUBLISHED", user.getUserId(), batch.status(),
                STATUS_PUBLISHED, null, manifest);
        return DataUploadSyncResponse.builder().batch(legacyService.getBatch(uploadId, user))
                .insertedRows(inserted).skippedRows(skipped).insertedRowsBySheet(insertedBySheet)
                .warnings(List.of()).build();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPublishFailure(Long uploadId, User user, String detail) {
        legacyService.requireCanReviewUploads(user);
        BatchState batch = lockBatch(uploadId);
        if (STATUS_PUBLISHED.equals(batch.status())) return;
        String message = "增量入库未完成，正式数据未发生变化，请修复后重试";
        jdbcTemplate.update("UPDATE data_upload_batches SET status=?, sync_error_message=? WHERE upload_id=?",
                STATUS_PUBLISH_FAILED, message, uploadId);
        Long releaseId = existingReleaseId(uploadId);
        if (releaseId != null) jdbcTemplate.update(
                "UPDATE dataset_releases SET status=?, error_message=? WHERE release_id=?",
                STATUS_PUBLISH_FAILED, message, releaseId);
        LOGGER.error("增量发布失败 uploadId={}, detail={}", uploadId, detail);
        insertAudit(uploadId, batch.currentPackageId(), "PUBLISH_FAILED", user.getUserId(), batch.status(),
                STATUS_PUBLISH_FAILED, message, null);
    }

    private Map<String, String> publishLiteratures(Long uploadId, Long releaseId, List<PackageRow> rows) {
        Map<String, String> result = new LinkedHashMap<>();
        int sequence = 1;
        for (PackageRow row : rows) {
            Map<String, String> data = row.data();
            if (!"通过".equals(value(data, "审核结论"))) continue;
            String candidate = value(data, "文献候选ID");
            String decision = value(data, "匹配决定");
            String code;
            if ("复用已有".equals(decision)) {
                code = value(data, "已有文献编号");
                if (queryOptionalString("SELECT literature_code FROM literatures WHERE literature_code=?", code) == null) {
                    throw new BusinessException("复用的已有文献不存在：" + code);
                }
                String storedDoi = queryOptionalString("SELECT doi FROM literatures WHERE literature_code=?", code);
                String reviewedDoi = normalizeDoi(value(data, "标准DOI"));
                if (!reviewedDoi.isBlank() && storedDoi != null && !normalizeDoi(storedDoi).equals(reviewedDoi)) {
                    throw new BusinessException("已有文献编号与DOI不一致：" + code);
                }
            } else {
                String reviewedDoi = normalizeDoi(value(data, "标准DOI"));
                String duplicateCode = reviewedDoi.isBlank() ? null : queryOptionalString(
                        "SELECT literature_code FROM literatures WHERE LOWER(REPLACE(doi,'https://doi.org/',''))=? LIMIT 1", reviewedDoi);
                if (duplicateCode != null) throw new BusinessException("DOI已属于正式文献，应选择复用已有：" + duplicateCode);
                code = value(data, "候选文献编号");
                if (code.isBlank()) code = "WBEN-" + uploadId + "-" + sequence++;
                jdbcTemplate.update("""
                        INSERT INTO literatures (literature_code, title, doi, keywords, abstract, upload_id, upload_row_id, raw_payload)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """, code, value(data, "文献标题"), nullIfBlank(value(data, "标准DOI")),
                        nullIfBlank(value(data, "keywords")), nullIfBlank(value(data, "abstract")), uploadId, row.rowId(), toJson(data));
            }
            result.put(candidate, code);
            markPublished(row, "LITERATURE", null);
        }
        return result;
    }

    private Map<String, String> buildWideRecord(List<PackageRow> group,
                                                Map<String, String> literatureCodes,
                                                Map<String, Map<String, String>> methodByHash,
                                                List<PackageRow> sites) {
        Map<String, String> first = group.getFirst().data();
        Map<String, String> wide = new LinkedHashMap<>();
        String candidate = value(first, "文献候选ID");
        String literatureCode = literatureCodes.get(candidate);
        if (literatureCode == null) throw new BusinessException("审核数据引用了未通过的文献：" + candidate);
        put(wide, "文献编号", literatureCode);
        copy(wide, first, Map.ofEntries(
                Map.entry("目标类别", "目标类别"), Map.entry("目标物质类别", "目标物质类别"),
                Map.entry("目标物质子类", "目标物质子类"), Map.entry("目标物质细类", "目标物质细类"),
                Map.entry("药物", "标准药物名称"), Map.entry("适应症", "标准适应症"),
                Map.entry("处方/非处方", "标准处方属性"), Map.entry("生物标记物名称", "标准生物标记物名称"),
                Map.entry("biomarker", "标准biomarker英文"), Map.entry("生物标记物CAS", "标准CAS"),
                Map.entry("理化性质", "理化性质原文"), Map.entry("样品采集时间", "样品采集时间原文"),
                Map.entry("采样开始时间_YYYY_MM", "采样开始年月"), Map.entry("采样结束时间_YYYY_MM", "采样结束年月"),
                Map.entry("来源记录编号", "来源记录编号"), Map.entry("来源工作簿说明", "来源定位原文")
        ));
        Map<String, String> method = methodByHash.get(SimplifiedUploadWorkbook.shortHash(
                value(first, "采样方法原文") + "\u001f" + value(first, "分析方法原文")));
        if (method == null) throw new BusinessException("找不到采样方法审核结果");
        put(wide, "采样方法", value(method, "标准采样方法"));
        put(wide, "分析方法", value(method, "标准分析方法"));
        PackageRow siteRow = sites.isEmpty() ? null : sites.getFirst();
        if (siteRow != null) {
            Map<String, String> site = siteRow.data();
            put(wide, "污水厂名称", value(site, "标准点位名称"));
            put(wide, "污水厂位置_国", value(site, "标准国家"));
            put(wide, "污水厂位置_省", value(site, "标准省州"));
            put(wide, "污水厂位置_市", value(site, "标准城市"));
            put(wide, "confirmed_site_id", value(site, "已有点位ID"));
            put(wide, "点位确认依据", value(site, "确认依据"));
            put(wide, "reported_site_key", value(site, "报告点位键"));
            put(wide, "污水厂处理规模（m3/day）", value(site, "处理规模原文"));
            put(wide, "汇水区人群数量", value(site, "汇水区人口原文"));
        }
        String doi = queryOptionalString("SELECT doi FROM literatures WHERE literature_code=?", literatureCode);
        put(wide, "DOI", doi);
        for (PackageRow row : group) applyMetric(wide, row.data());
        return wide;
    }

    private void applyMetric(Map<String, String> wide, Map<String, String> row) {
        String metric = value(row, "指标类型原文");
        String statistic = value(row, "统计量原文");
        String value = value(row, "标准数值");
        String unit = value(row, "标准单位");
        switch (metric) {
            case "MDL", "MQL", "IDL", "IQL" -> { put(wide, metric + "_value", value); put(wide, metric + "_unit", unit); }
            case "进水浓度" -> {
                String suffix = switch (statistic) { case "min" -> "min"; case "max" -> "max"; case "median" -> "median"; default -> "average"; };
                put(wide, "进水浓度" + suffix + "_value", value); put(wide, "进水浓度" + suffix + "_unit", unit);
                if (Set.of("直接值", "average", "median").contains(statistic)) { put(wide, "做图浓度_value", value); put(wide, "做图浓度_unit", unit); }
            }
            case "每日质量负荷DLs" -> { put(wide, "每日质量负荷DLs", value); put(wide, "DLs_unit", unit); }
            case "PNDL直接值" -> { put(wide, "PNDL_value", value); put(wide, "PNDL_unit", unit); put(wide, "做图PNDL_value", value); put(wide, "做图PNDL_unit", unit); }
            case "校准系数" -> put(wide, "校准系数", value);
            case "GS管道衰减系数" -> put(wide, "GS管道衰减系数", value);
            case "人体排泄率" -> put(wide, "人体排泄率（%）", value);
            case "药物消费量" -> { put(wide, "药物消费量_value", value); put(wide, "药物消费量_unit", unit); }
            case "药物使用流行率" -> put(wide, "药物使用流行率（%）", value);
            case "疾病患病率" -> put(wide, "疾病患病率（%）", value);
            default -> throw new BusinessException("不支持的指标类型：" + metric);
        }
    }

    private long insertPublishedMeasurement(Long uploadId, Long releaseId, Long uploadRowId,
                                            String recordKey, Map<String, String> data) {
        long compoundId = getOrCreateCompound(data);
        long methodId = getOrCreateMethod(data);
        long plantId = getOrCreatePlant(data);
        String reportedSiteKey = nullIfBlank(value(data, "reported_site_key"));
        long eventId = insertAndReturnKey("""
                INSERT INTO sampling_events (plant_id, reported_site_key, sample_collection_time,
                    sampling_start_ym, sampling_end_ym, source_workbook, original_row_number)
                VALUES (?, ?, NULL, ?, ?, ?, NULL)
                """, plantId, reportedSiteKey, nullIfBlank(value(data, "采样开始时间_YYYY_MM")),
                nullIfBlank(value(data, "采样结束时间_YYYY_MM")), nullIfBlank(value(data, "来源工作簿说明")));
        return insertAndReturnKey("""
                INSERT INTO measurements (
                    compound_id, method_id, event_id, upload_id, upload_row_id, literature_code,
                    raw_payload, dedupe_key, record_key, dataset_release_id,
                    plot_concentration_value, plot_concentration_unit, inflow_min_value, inflow_min_unit,
                    inflow_max_value, inflow_max_unit, inflow_avg_value, inflow_avg_unit,
                    inflow_median_value, inflow_median_unit, daily_load_dls_value, daily_load_dls_unit,
                    pndl_value, pndl_unit, pndl_estimated_value, pndl_estimated_unit, plot_pndl_value, plot_pndl_unit
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)
                """, compoundId, methodId, eventId, uploadId, uploadRowId, value(data, "文献编号"), toJson(data),
                recordKey, recordKey, releaseId,
                decimal(data, "做图浓度_value"), nullIfBlank(value(data, "做图浓度_unit")),
                decimal(data, "进水浓度min_value"), nullIfBlank(value(data, "进水浓度min_unit")),
                decimal(data, "进水浓度max_value"), nullIfBlank(value(data, "进水浓度max_unit")),
                decimal(data, "进水浓度average_value"), nullIfBlank(value(data, "进水浓度average_unit")),
                decimal(data, "进水浓度median_value"), nullIfBlank(value(data, "进水浓度median_unit")),
                decimal(data, "每日质量负荷DLs"), nullIfBlank(value(data, "DLs_unit")),
                decimal(data, "PNDL_value"), nullIfBlank(value(data, "PNDL_unit")),
                decimal(data, "做图PNDL_value"), nullIfBlank(value(data, "做图PNDL_unit")));
    }

    private long getOrCreateCompound(Map<String, String> data) {
        Object[] args = { value(data, "药物"), value(data, "目标类别"), value(data, "目标物质类别"),
                nullIfBlank(value(data, "目标物质子类")), nullIfBlank(value(data, "目标物质细类")),
                nullIfBlank(value(data, "生物标记物名称")), nullIfBlank(value(data, "生物标记物CAS")) };
        Long existing = queryOptionalLong("""
                SELECT compound_id FROM compounds WHERE drug_name=? AND target_category=? AND substance_category=?
                AND COALESCE(substance_subclass,'')=COALESCE(?,'') AND COALESCE(substance_fine,'')=COALESCE(?,'')
                AND COALESCE(biomarker_name,'')=COALESCE(?,'') AND COALESCE(biomarker_cas,'')=COALESCE(?,'') LIMIT 1
                """, args);
        if (existing != null) return existing;
        return insertAndReturnKey("""
                INSERT INTO compounds (target_category, substance_category, substance_subclass, substance_fine,
                    drug_name, indications, prescription_type, biomarker_name, biomarker_cas,
                    physicochemical_properties, calibration_coefficient, human_excretion_rate,
                    consumption_value, consumption_unit, usage_prevalence, disease_prevalence, keywords, doi, abstract)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?, NULL)
                """, value(data, "目标类别"), value(data, "目标物质类别"), nullIfBlank(value(data, "目标物质子类")),
                nullIfBlank(value(data, "目标物质细类")), value(data, "药物").isBlank() ? value(data, "生物标记物名称") : value(data, "药物"),
                nullIfBlank(value(data, "适应症")), safePrescription(value(data, "处方/非处方")),
                nullIfBlank(value(data, "生物标记物名称")), nullIfBlank(value(data, "生物标记物CAS")),
                nullIfBlank(value(data, "理化性质")), decimal(data, "校准系数"), decimal(data, "人体排泄率（%）"),
                decimal(data, "药物消费量_value"), nullIfBlank(value(data, "药物消费量_unit")),
                decimal(data, "药物使用流行率（%）"), decimal(data, "疾病患病率（%）"), nullIfBlank(value(data, "DOI")));
    }

    private long getOrCreateMethod(Map<String, String> data) {
        Long existing = queryOptionalLong("""
                SELECT method_id FROM analytical_methods WHERE sampling_method=? AND analysis_method=?
                AND COALESCE(mdl_value,-1)=COALESCE(?,-1) AND COALESCE(mdl_unit,'')=COALESCE(?,'')
                AND COALESCE(mql_value,-1)=COALESCE(?,-1) AND COALESCE(mql_unit,'')=COALESCE(?,'')
                AND COALESCE(idl_value,-1)=COALESCE(?,-1) AND COALESCE(idl_unit,'')=COALESCE(?,'')
                AND COALESCE(iql_value,-1)=COALESCE(?,-1) AND COALESCE(iql_unit,'')=COALESCE(?,'') LIMIT 1
                """, value(data, "采样方法"), value(data, "分析方法"), decimal(data, "MDL_value"), nullIfBlank(value(data, "MDL_unit")),
                decimal(data, "MQL_value"), nullIfBlank(value(data, "MQL_unit")), decimal(data, "IDL_value"), nullIfBlank(value(data, "IDL_unit")),
                decimal(data, "IQL_value"), nullIfBlank(value(data, "IQL_unit")));
        if (existing != null) return existing;
        return insertAndReturnKey("""
                INSERT INTO analytical_methods (sampling_method, analysis_method, mdl_value, mdl_unit, mql_value, mql_unit,
                    idl_value, idl_unit, iql_value, iql_unit) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, value(data, "采样方法"), value(data, "分析方法"), decimal(data, "MDL_value"), nullIfBlank(value(data, "MDL_unit")),
                decimal(data, "MQL_value"), nullIfBlank(value(data, "MQL_unit")), decimal(data, "IDL_value"), nullIfBlank(value(data, "IDL_unit")),
                decimal(data, "IQL_value"), nullIfBlank(value(data, "IQL_unit")));
    }

    private long getOrCreatePlant(Map<String, String> data) {
        String name = value(data, "污水厂名称").isBlank() ? "NA" : value(data, "污水厂名称");
        String country = value(data, "污水厂位置_国").isBlank() ? "NA" : value(data, "污水厂位置_国");
        String province = value(data, "污水厂位置_省").isBlank() ? "NA" : value(data, "污水厂位置_省");
        String city = value(data, "污水厂位置_市").isBlank() ? "NA" : value(data, "污水厂位置_市");
        Long existing = queryOptionalLong("SELECT plant_id FROM wastewater_plants WHERE plant_name=? AND country=? AND province=? AND city=? LIMIT 1",
                name, country, province, city);
        if (existing != null) return existing;
        return insertAndReturnKey("""
                INSERT INTO wastewater_plants (plant_name, treatment_capacity_m3_day, served_population,
                    country, province, city, gs_attenuation_coefficient) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, name, longValue(data, "污水厂处理规模（m3/day）"), longValue(data, "汇水区人群数量"),
                country, province, city, decimal(data, "GS管道衰减系数"));
    }

    private void publishSites(Long uploadId, Long measurementId, String groupId, List<PackageRow> sites,
                              Map<String, String> publishedRecord) {
        if (sites.isEmpty()) {
            jdbcTemplate.update("""
                    INSERT INTO record_site_bridge (upload_id, upload_row_id, excel_row_number, internal_record_key,
                        measurement_id, reported_site_key, effective_site_key, match_status)
                    VALUES (?, NULL, NULL, ?, ?, NULL, NULL, 'UNMATCHED')
                    """, uploadId, "record:" + measurementId, measurementId);
            return;
        }
        for (PackageRow row : sites) {
            Map<String, String> site = row.data();
            String key = value(site, "报告点位键");
            if (key.isBlank()) key = "RPS-" + SimplifiedUploadWorkbook.shortHash(groupId + "\u001f" + value(site, "标准点位名称"));
            jdbcTemplate.update("""
                    INSERT INTO reported_sites (reported_site_key, literature_code, raw_plant_name, canonical_plant_name,
                        country, province, city, key_quality, confirmed_site_id, confirmation_evidence,
                        include_in_point_count, site_note, upload_id, upload_row_id, excel_row_number)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 'REVIEWED', ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE canonical_plant_name=VALUES(canonical_plant_name), country=VALUES(country),
                        province=VALUES(province), city=VALUES(city), include_in_point_count=VALUES(include_in_point_count),
                        site_note=VALUES(site_note)
                    """, key, fallback(value(site, "文献编号"), value(publishedRecord, "文献编号"), "NA"),
                    nullIfBlank(value(site, "点位名称原文")), nullIfBlank(value(site, "标准点位名称")),
                    nullIfBlank(value(site, "标准国家")), nullIfBlank(value(site, "标准省州")), nullIfBlank(value(site, "标准城市")),
                    nullIfBlank(value(site, "已有点位ID")), nullIfBlank(value(site, "确认依据")),
                    "是".equals(value(site, "是否计入统计")), nullIfBlank(value(site, "关联说明")), uploadId, row.rowId(), row.excelRowNumber());
            String effective = value(site, "已有点位ID").isBlank() ? key : value(site, "已有点位ID");
            jdbcTemplate.update("""
                    INSERT INTO record_site_bridge (upload_id, upload_row_id, excel_row_number, internal_record_key,
                        measurement_id, reported_site_key, effective_site_key, match_status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, uploadId, row.rowId(), row.excelRowNumber(), "record:" + measurementId,
                    measurementId, key, effective, sites.size() > 1 ? "MULTI_SITE" : "EXACT");
            markPublished(row, "REPORTED_SITE", null);
        }
    }

    private void insertHomeTarget(Long measurementId, Long uploadId, Map<String, String> data) {
        String doi = nullIfBlank(value(data, "DOI"));
        jdbcTemplate.update("""
                INSERT INTO home_target_records (literature_id, doi, target_category, target_group,
                    substance_category, substance_subclass, substance_fine, biomarker_name,
                    source_sheet, source_row_number, published_measurement_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, value(data, "文献编号"), doi, value(data, "目标类别"),
                value(data, "目标类别").contains("药物") ? "drug" : "consumer", value(data, "目标物质类别"),
                value(data, "目标物质子类"), nullIfBlank(value(data, "目标物质细类")), value(data, "生物标记物名称"),
                "增量发布:" + uploadId, Math.toIntExact(measurementId), measurementId);
    }

    private int publishIcd11(Long uploadId, Long releaseId, List<PackageRow> rows,
                             Map<String, String> literatureCodes, List<PackageRow> normalizedRows) {
        Map<String, Map<String, String>> normalizedByCandidate = normalizedRows.stream().collect(Collectors.toMap(
                row -> value(row.data(), "文献候选ID"), PackageRow::data, (first, ignored) -> first));
        int inserted = 0;
        for (PackageRow row : rows) {
            Map<String, String> data = row.data();
            if (!"通过".equals(value(data, "审核结论"))) { markPublished(row, "REVIEW_EVIDENCE", null); continue; }
            Map<String, String> normalized = normalizedByCandidate.getOrDefault(value(data, "文献候选ID"), Map.of());
            long pathId = insertAndReturnKey("""
                    INSERT INTO icd11_sankey_paths (target_category, substance_category, substance_subclass, substance_fine,
                        drug_name, indication_original, biomarker_name, biomarker_alias, normalized_indication, disease_entity,
                        icd11_level1_code, icd11_level1_name, icd11_level2_code, icd11_level2_name,
                        icd11_level3_code, icd11_level3_name, mapping_level, match_type, in_sankey, exclusion_reason,
                        review_status, note, biomarker_cas, literature_count, data_row_count, unique_doi_count,
                        missing_doi_count, upload_id, upload_row_id, raw_payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, 0, 0, 0, ?, ?, ?)
                    """, fallback(value(data, "目标类别"), value(normalized, "目标类别"), "未分类"),
                    fallback(value(data, "目标物质类别"), value(normalized, "目标物质类别"), "未分类"),
                    fallback(value(data, "目标物质子类"), value(normalized, "目标物质子类"), "未分类"),
                    nullIfBlank(fallback(value(data, "目标物质细类"), value(normalized, "目标物质细类"), "")),
                    fallback(value(data, "标准药物名称"), value(normalized, "标准药物名称"), "未命名药物"),
                    value(normalized, "适应症原文"), fallback(value(data, "标准生物标记物名称"), value(normalized, "标准生物标记物名称"), "未命名生物标记物"),
                    nullIfBlank(value(normalized, "标准biomarker英文")), value(data, "标准适应症"), nullIfBlank(value(data, "疾病实体")),
                    nullIfBlank(value(data, "ICD11一级编码")), fallback(value(data, "ICD11一级名称"), "未分类"),
                    nullIfBlank(value(data, "ICD11二级编码")), fallback(value(data, "ICD11二级名称"), "未分类"),
                    nullIfBlank(value(data, "ICD11三级编码")), nullIfBlank(value(data, "ICD11三级名称")),
                    fallback(value(data, "映射层级"), "Level2"), nullIfBlank(value(data, "匹配类型")),
                    "是".equals(value(data, "是否进入Sankey")), nullIfBlank(value(data, "排除原因")),
                    value(data, "审核结论"), nullIfBlank(value(data, "备注")), nullIfBlank(value(normalized, "标准CAS")),
                    uploadId, row.rowId(), toJson(data));
            String code = literatureCodes.get(value(data, "文献候选ID"));
            if (code != null) jdbcTemplate.update("""
                    INSERT INTO icd11_sankey_path_sources (sankey_path_id, source_order, literature_code, doi)
                    VALUES (?, 1, ?, (SELECT doi FROM literatures WHERE literature_code=?))
                    """, pathId, code, code);
            markPublished(row, "ICD11_SANKEY_PATH", pathId);
            inserted++;
        }
        return inserted;
    }

    private void refreshDerivedReadModels(Map<String, Object> manifest) {
        try {
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.setContinueOnError(false);
            populator.addScript(new ClassPathResource("db/map_pndl_stats_refresh_v2.sql"));
            populator.execute(dataSource);
            manifest.put("mapStatistics", "rebuilt");
        } catch (RuntimeException exception) {
            throw new BusinessException("地图派生数据重建失败");
        }
        manifest.put("homeStatistics", "incrementallyUpdated");
        manifest.put("coreMarkerPriority", "databaseFactsUpdated; managed scoring rebuild remains versioned");
    }

    private DataUploadSyncResponse publishedResponse(Long uploadId, User user, Long releaseId) {
        Map<String, Object> release = jdbcTemplate.queryForMap("SELECT inserted_records, skipped_records, manifest_json FROM dataset_releases WHERE release_id=?", releaseId);
        return DataUploadSyncResponse.builder().batch(legacyService.getBatch(uploadId, user))
                .insertedRows(((Number) release.get("inserted_records")).intValue())
                .skippedRows(((Number) release.get("skipped_records")).intValue())
                .insertedRowsBySheet(Map.of()).warnings(List.of("该批次已经发布，本次未重复写入")).build();
    }

    private List<PackageRow> packageRows(Long packageId) {
        return jdbcTemplate.query("""
                SELECT row_id, sheet_name, excel_row_number, raw_json FROM data_upload_rows
                WHERE review_package_id=? AND row_stage='REVIEW_PACKAGE' AND row_status<>'ERROR'
                ORDER BY sheet_name, excel_row_number
                """, (rs, rowNum) -> new PackageRow(rs.getLong("row_id"), rs.getString("sheet_name"),
                rs.getInt("excel_row_number"), fromJsonMap(rs.getString("raw_json"))), packageId);
    }

    private void markGroupPublished(List<PackageRow> rows, Long measurementId) {
        rows.forEach(row -> markPublished(row, "MEASUREMENT", measurementId));
    }
    private void markPublished(PackageRow row, String entityType, Long entityId) {
        jdbcTemplate.update("""
                UPDATE data_upload_rows SET row_status='SYNCED', synced_entity_type=?, synced_entity_id=?,
                    synced_measurement_id=CASE WHEN ?='MEASUREMENT' THEN ? ELSE synced_measurement_id END WHERE row_id=?
                """, entityType, entityId, entityType, entityId, row.rowId());
    }

    private String stableRecordKey(Map<String, String> data) {
        String canonical = String.join("\u001f", "wbe-record-v2", value(data, "文献编号"), value(data, "来源记录编号"),
                value(data, "生物标记物名称"), value(data, "采样开始时间_YYYY_MM"), value(data, "采样结束时间_YYYY_MM"),
                value(data, "污水厂位置_国"), value(data, "污水厂位置_省"), value(data, "污水厂位置_市"), value(data, "污水厂名称"));
        return sha256(canonical);
    }

    private Long existingReleaseId(Long uploadId) { return queryOptionalLong("SELECT release_id FROM dataset_releases WHERE upload_id=?", uploadId); }
    private Long queryOptionalLong(String sql, Object... args) { try { return jdbcTemplate.queryForObject(sql, Long.class, args); } catch (EmptyResultDataAccessException exception) { return null; } }
    private String queryOptionalString(String sql, Object... args) { try { return jdbcTemplate.queryForObject(sql, String.class, args); } catch (EmptyResultDataAccessException exception) { return null; } }
    private String normalizeDoi(String value) { return normalize(value).replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "").replaceFirst("^doi\\s*:\\s*", ""); }
    private String sha256(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); } }
    private BigDecimal decimal(Map<String, String> data, String key) { String value = value(data, key); return value.isBlank() ? null : new BigDecimal(value); }
    private Long longValue(Map<String, String> data, String key) { String value = value(data, key); return value.isBlank() ? null : new BigDecimal(value).longValue(); }
    private String nullIfBlank(String value) { return value == null || value.isBlank() ? null : value; }
    private String safePrescription(String value) { return Set.of("处方药", "非处方药", "其他").contains(value) ? value : "其他"; }
    private String fallback(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private void put(Map<String, String> target, String key, String value) { target.put(key, value == null ? "" : value.trim()); }
    private void copy(Map<String, String> target, Map<String, String> source, Map<String, String> mapping) { mapping.forEach((targetKey, sourceKey) -> put(target, targetKey, value(source, sourceKey))); }

    private record PackageRow(Long rowId, String sheetName, int excelRowNumber, Map<String, String> data) { }

    private void validateReviewPackage(SimplifiedUploadWorkbook.ParsedReview parsed,
                                       List<SimplifiedUploadWorkbook.SubmissionRow> submissions) {
        if (!parsed.workbookErrors().isEmpty()) return;
        Map<String, SimplifiedUploadWorkbook.SubmissionRow> sourceById = submissions.stream()
                .collect(Collectors.toMap(SimplifiedUploadWorkbook.SubmissionRow::submissionRowId, Function.identity()));
        List<SimplifiedUploadWorkbook.ReviewRow> normalized = parsed.rowsBySheet().getOrDefault(NORMALIZED_SHEET, List.of());
        Set<String> seenRows = new LinkedHashSet<>();
        Set<String> groupIds = new LinkedHashSet<>();
        Set<String> candidateIds = new LinkedHashSet<>();
        Set<String> metricKeys = new LinkedHashSet<>();
        for (SimplifiedUploadWorkbook.ReviewRow row : normalized) {
            Map<String, String> data = row.values();
            String submissionRowId = value(data, "投稿行ID");
            SimplifiedUploadWorkbook.SubmissionRow source = sourceById.get(submissionRowId);
            if (source == null) row.errors().add("投稿行ID不存在于当前投稿版本");
            if (!seenRows.add(submissionRowId)) row.errors().add("投稿行ID在规范数据记录中重复");
            if (source != null) validateLockedSourceFields(row, source.values());
            String disposition = value(data, "记录处置");
            if (!Set.of("发布", "排除").contains(disposition)) row.errors().add("记录处置必须为发布或排除");
            if ("排除".equals(disposition) && value(data, "排除原因").isBlank()) row.errors().add("排除记录必须填写排除原因");
            if ("发布".equals(disposition)) {
                requireFields(row, List.of("记录组ID", "文献候选ID", "目标类别", "目标物质类别", "目标物质子类",
                        "目标物质细类", "标准生物标记物名称", "标准数值", "标准单位"));
                if (!isDecimal(value(data, "标准数值"))) row.errors().add("发布记录的标准数值必须是可入库数值");
                String metricKey = value(data, "记录组ID") + "\u001f" + value(data, "指标类型原文") + "\u001f" + value(data, "统计量原文");
                if (!metricKeys.add(metricKey)) row.errors().add("同一记录组的指标类型和统计量重复");
                if (source != null && corrected(data, source.values()) && value(data, "纠正原因").isBlank()) {
                    row.errors().add("身份、数值、单位或时间发生纠正时必须填写纠正原因");
                }
            }
            groupIds.add(value(data, "记录组ID"));
            candidateIds.add(value(data, "文献候选ID"));
        }
        Set<String> missing = new LinkedHashSet<>(sourceById.keySet()); missing.removeAll(seenRows);
        if (!missing.isEmpty()) parsed.workbookErrors().add("规范数据记录缺少 " + missing.size() + " 个投稿行ID");

        validateReferenceCoverage(parsed, LITERATURE_SHEET, "文献候选ID", candidateIds);
        validateReferenceCoverage(parsed, SITE_SHEET, "记录组ID", groupIds);
        validateMethodCoverage(parsed, submissions);
        validateLiteratures(parsed.rowsBySheet().getOrDefault(LITERATURE_SHEET, List.of()));
        validateSites(parsed.rowsBySheet().getOrDefault(SITE_SHEET, List.of()));
        validateMethods(parsed.rowsBySheet().getOrDefault(METHOD_SHEET, List.of()));
        validateIcd11(parsed.rowsBySheet().getOrDefault(ICD11_SHEET, List.of()));
    }

    private void validateLockedSourceFields(SimplifiedUploadWorkbook.ReviewRow row, Map<String, String> source) {
        Map<String, String> locked = Map.ofEntries(
                Map.entry("来源记录编号", "来源记录编号"), Map.entry("目标物药物原文", "目标物/药物原文"),
                Map.entry("适应症原文", "适应症原文"), Map.entry("处方属性原文", "处方属性原文"),
                Map.entry("生物标记物名称原文", "生物标记物名称原文"), Map.entry("biomarker英文原文", "biomarker英文原文"),
                Map.entry("CAS原文", "CAS原文"), Map.entry("理化性质原文", "理化性质原文"),
                Map.entry("采样方法原文", "采样方法原文"), Map.entry("分析方法原文", "分析方法原文"),
                Map.entry("点位类型原文", "点位类型"), Map.entry("点位名称原文", "点位名称原文"),
                Map.entry("国家原文", "国家原文"), Map.entry("省州原文", "省州原文"), Map.entry("城市原文", "城市原文"),
                Map.entry("样品采集时间原文", "样品采集时间原文"), Map.entry("指标类型原文", "指标类型"),
                Map.entry("统计量原文", "统计量"), Map.entry("原始数值", "原始数值"), Map.entry("原始单位", "原始单位"),
                Map.entry("数值来源原文", "数值来源"), Map.entry("来源定位原文", "页码表号Sheet图号"), Map.entry("原文证据", "原文证据")
        );
        locked.forEach((reviewField, sourceField) -> {
            if (!Objects.equals(normalize(row.values().get(reviewField)), normalize(source.get(sourceField)))) {
                row.errors().add("系统锁定原始字段被修改：" + reviewField);
            }
        });
        String expectedCandidate = SimplifiedUploadWorkbook.literatureCandidateId(source);
        String expectedGroup = SimplifiedUploadWorkbook.recordGroupId(expectedCandidate, source);
        if (!expectedCandidate.equals(value(row.values(), "文献候选ID"))) row.errors().add("文献候选ID被修改");
        if (!expectedGroup.equals(value(row.values(), "记录组ID"))) row.errors().add("记录组ID被修改");
    }

    private void validateReferenceCoverage(SimplifiedUploadWorkbook.ParsedReview parsed, String sheetName,
                                           String keyField, Set<String> expected) {
        Set<String> actual = parsed.rowsBySheet().getOrDefault(sheetName, List.of()).stream()
                .map(row -> value(row.values(), keyField)).filter(value -> !value.isBlank()).collect(Collectors.toSet());
        Set<String> missing = new LinkedHashSet<>(expected); missing.remove(""); missing.removeAll(actual);
        if (!missing.isEmpty()) parsed.workbookErrors().add(sheetName + "缺少 " + missing.size() + " 个" + keyField);
    }

    private void validateMethodCoverage(SimplifiedUploadWorkbook.ParsedReview parsed,
                                        List<SimplifiedUploadWorkbook.SubmissionRow> submissions) {
        Set<String> expected = submissions.stream().map(row -> SimplifiedUploadWorkbook.shortHash(
                value(row.values(), "采样方法原文") + "\u001f" + value(row.values(), "分析方法原文"))).collect(Collectors.toSet());
        Set<String> actual = parsed.rowsBySheet().getOrDefault(METHOD_SHEET, List.of()).stream()
                .map(row -> value(row.values(), "原始方法哈希")).collect(Collectors.toSet());
        expected.removeAll(actual);
        if (!expected.isEmpty()) parsed.workbookErrors().add(METHOD_SHEET + "缺少 " + expected.size() + " 个原始方法映射");
    }

    private void validateLiteratures(List<SimplifiedUploadWorkbook.ReviewRow> rows) {
        Set<String> candidates = new LinkedHashSet<>();
        for (var row : rows) {
            requireFields(row, List.of("文献候选ID", "匹配决定", "文献标题", "发表年份", "期刊/来源", "来源文件名或URL", "审核结论"));
            if (!candidates.add(value(row.values(), "文献候选ID"))) row.errors().add("文献候选ID重复");
            if ("复用已有".equals(value(row.values(), "匹配决定")) && value(row.values(), "已有文献编号").isBlank()) row.errors().add("复用已有文献必须填写已有文献编号");
            if (value(row.values(), "标准DOI").isBlank() && (value(row.values(), "文献标题").isBlank()
                    || value(row.values(), "发表年份").isBlank() || value(row.values(), "期刊/来源").isBlank()
                    || value(row.values(), "来源文件名或URL").isBlank())) row.errors().add("无DOI时替代识别字段不完整");
        }
    }

    private void validateSites(List<SimplifiedUploadWorkbook.ReviewRow> rows) {
        for (var row : rows) {
            requireFields(row, List.of("点位审核ID", "记录组ID", "标准点位名称", "标准国家", "是否计入统计", "审核结论"));
            if (!value(row.values(), "已有点位ID").isBlank() && value(row.values(), "确认依据").isBlank()) row.errors().add("关联已有点位必须填写确认依据");
            if ("排除".equals(value(row.values(), "审核结论")) && value(row.values(), "关联说明").isBlank()) row.errors().add("排除点位必须填写关联说明");
        }
    }

    private void validateMethods(List<SimplifiedUploadWorkbook.ReviewRow> rows) {
        for (var row : rows) requireFields(row, List.of("方法审核ID", "文献候选ID", "原始方法哈希", "原始采样方法", "标准采样方法", "标准分析方法", "审核结论"));
    }

    private void validateIcd11(List<SimplifiedUploadWorkbook.ReviewRow> rows) {
        for (var row : rows) {
            requireFields(row, List.of("映射审核ID", "来源适应症组ID", "文献候选ID", "标准适应症", "是否进入Sankey", "审核结论"));
            if ("是".equals(value(row.values(), "是否进入Sankey"))) requireFields(row, List.of("疾病实体", "ICD11一级名称", "ICD11二级名称", "映射层级", "匹配类型", "证据"));
            if ("否".equals(value(row.values(), "是否进入Sankey")) && value(row.values(), "排除原因").isBlank()) row.errors().add("不进入Sankey必须填写排除原因");
        }
    }

    private Map<String, Object> buildImpactSummary(SimplifiedUploadWorkbook.ParsedReview parsed) {
        List<SimplifiedUploadWorkbook.ReviewRow> normalized = parsed.rowsBySheet().getOrDefault(NORMALIZED_SHEET, List.of());
        long publishedRows = normalized.stream().filter(row -> "发布".equals(value(row.values(), "记录处置"))).count();
        long excludedRows = normalized.size() - publishedRows;
        long groups = normalized.stream().filter(row -> "发布".equals(value(row.values(), "记录处置")))
                .map(row -> value(row.values(), "记录组ID")).distinct().count();
        return new LinkedHashMap<>(Map.of(
                "riskLevel", "INCREMENTAL",
                "submissionRows", normalized.size(),
                "publishRows", publishedRows,
                "excludedRows", excludedRows,
                "newRecordGroups", groups,
                "existingRowsDeleted", 0
        ));
    }

    private void persistFieldChanges(Long uploadId, Long packageId, Long userId,
                                     SimplifiedUploadWorkbook.ParsedReview parsed,
                                     List<SimplifiedUploadWorkbook.SubmissionRow> submissions) {
        Map<String, Map<String, String>> source = submissions.stream().collect(Collectors.toMap(
                SimplifiedUploadWorkbook.SubmissionRow::submissionRowId, SimplifiedUploadWorkbook.SubmissionRow::values));
        for (var row : parsed.rowsBySheet().getOrDefault(NORMALIZED_SHEET, List.of())) {
            Map<String, String> raw = source.get(value(row.values(), "投稿行ID"));
            if (raw == null) continue;
            Map<String, String> comparison = Map.of(
                    "标准生物标记物名称", value(raw, "生物标记物名称原文"),
                    "标准biomarker英文", value(raw, "biomarker英文原文"),
                    "标准CAS", value(raw, "CAS原文"),
                    "标准数值", value(raw, "原始数值"),
                    "标准单位", value(raw, "原始单位")
            );
            for (String field : CORRECTED_FIELDS) {
                String oldValue = comparison.getOrDefault(field, "");
                String newValue = value(row.values(), field);
                if (!normalize(oldValue).equals(normalize(newValue)) && !newValue.isBlank()) {
                    jdbcTemplate.update("""
                            INSERT INTO data_upload_field_changes (
                                upload_id, package_id, submission_row_id, field_name,
                                old_value, new_value, reason, changed_by
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                            """, uploadId, packageId, value(row.values(), "投稿行ID"), field,
                            oldValue, newValue, requiredReason(value(row.values(), "纠正原因")), userId);
                }
            }
        }
    }

    private boolean corrected(Map<String, String> review, Map<String, String> source) {
        return !normalize(value(review, "标准生物标记物名称")).equals(normalize(value(source, "生物标记物名称原文")))
                || !normalize(value(review, "标准biomarker英文")).equals(normalize(value(source, "biomarker英文原文")))
                || !normalize(value(review, "标准CAS")).equals(normalize(value(source, "CAS原文")))
                || !normalize(value(review, "标准数值")).equals(normalize(value(source, "原始数值")))
                || !normalize(value(review, "标准单位")).equals(normalize(value(source, "原始单位")))
                || !value(review, "采样开始年月").isBlank() || !value(review, "采样结束年月").isBlank();
    }

    private DataUploadPreviewResponse previewResponse(long uploadId, User user,
                                                      SimplifiedUploadWorkbook.ParsedSubmission parsed,
                                                      List<DataUploadRowResponse> rows) {
        List<String> errors = flattenedSubmissionErrors(parsed);
        return DataUploadPreviewResponse.builder()
                .batch(legacyService.getBatch(uploadId, user))
                .requiredHeaders(SUBMISSION_HEADERS)
                .optionalHeaders(List.of())
                .headerErrors(parsed.workbookErrors())
                .batchWarnings(errors)
                .previewRows(rows.stream().limit(PREVIEW_LIMIT).toList())
                .sheetSummaries(List.of(sheetSummary(SUBMISSION_SHEET, rows)))
                .previewRowsBySheet(Map.of(SUBMISSION_SHEET, rows.stream().limit(PREVIEW_LIMIT).toList()))
                .requiredReviewSheets(REVIEW_SHEETS)
                .build();
    }

    private DataUploadSheetSummaryResponse sheetSummary(String sheet, List<DataUploadRowResponse> rows) {
        int errors = (int) rows.stream().filter(row -> "ERROR".equals(row.getStatus())).count();
        int warnings = (int) rows.stream().filter(row -> "WARNING".equals(row.getStatus())).count();
        return DataUploadSheetSummaryResponse.builder().sheetName(sheet).totalRows(rows.size())
                .validRows(rows.size() - errors).warningRows(warnings).errorRows(errors).build();
    }

    private List<DataUploadRowResponse> insertSubmissionRows(Long uploadId, int version,
                                                              List<SimplifiedUploadWorkbook.SubmissionRow> rows) {
        List<DataUploadRowResponse> result = new ArrayList<>();
        for (var row : rows) {
            String status = row.valid() ? "VALID" : "ERROR";
            long id = insertAndReturnKey("""
                    INSERT INTO data_upload_rows (
                        upload_id, review_package_id, row_stage, sheet_name, excel_row_number,
                        row_status, raw_json, error_json, warning_json, row_fingerprint,
                        submission_row_id, submission_version
                    ) VALUES (?, NULL, 'SUBMISSION', ?, ?, ?, ?, ?, ?, NULL, ?, ?)
                    """, uploadId, SUBMISSION_SHEET, row.excelRowNumber(), status, toJson(row.values()),
                    toJson(row.errors()), "[]", row.submissionRowId(), version);
            result.add(DataUploadRowResponse.builder().rowId(id).rowStage("SUBMISSION").sheetName(SUBMISSION_SHEET)
                    .excelRowNumber(row.excelRowNumber()).status(status).data(row.values()).errors(row.errors())
                    .warnings(List.of()).build());
        }
        return result;
    }

    private void insertReviewRows(Long uploadId, Long packageId, SimplifiedUploadWorkbook.ParsedReview parsed) {
        for (String sheet : REVIEW_SHEETS) {
            for (var row : parsed.rowsBySheet().getOrDefault(sheet, List.of())) {
                String status = row.valid() ? "VALID" : "ERROR";
                jdbcTemplate.update("""
                        INSERT INTO data_upload_rows (
                            upload_id, review_package_id, row_stage, sheet_name, excel_row_number,
                            row_status, raw_json, error_json, warning_json, row_fingerprint,
                            submission_row_id, submission_version
                        ) VALUES (?, ?, 'REVIEW_PACKAGE', ?, ?, ?, ?, ?, '[]', NULL, ?, 1)
                        """, uploadId, packageId, sheet, row.excelRowNumber(), status, toJson(row.values()),
                        toJson(row.errors()), NORMALIZED_SHEET.equals(sheet) ? value(row.values(), "投稿行ID") : null);
            }
        }
    }

    private List<SimplifiedUploadWorkbook.SubmissionRow> currentSubmissionRows(Long uploadId, int version) {
        return jdbcTemplate.query("""
                SELECT excel_row_number, submission_row_id, raw_json, error_json
                FROM data_upload_rows
                WHERE upload_id=? AND row_stage='SUBMISSION' AND submission_version=?
                ORDER BY excel_row_number
                """, (rs, rowNum) -> new SimplifiedUploadWorkbook.SubmissionRow(
                rs.getInt("excel_row_number"), rs.getString("submission_row_id"),
                fromJsonMap(rs.getString("raw_json")), fromJsonList(rs.getString("error_json"))), uploadId, version);
    }

    private void insertRevision(Long uploadId, int version, StoredUpload source, Long userId, String status,
                                int total, int valid, int errors, List<String> messages) {
        jdbcTemplate.update("""
                INSERT INTO data_upload_submission_revisions (
                    upload_id, version_no, file_name, stored_file_path, sha256, submitted_by,
                    status, total_rows, valid_rows, error_rows, validation_message
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, uploadId, version, source.fileName(), source.path().toString(), source.sha256(), userId,
                status, total, valid, errors, toJson(messages));
    }

    private DataUploadReviewPackageResponse reviewPackageResponse(Long packageId) {
        Map<String, Object> packageRow = jdbcTemplate.queryForMap("""
                SELECT p.*, u.username AS uploaded_by_name FROM data_upload_review_packages p
                JOIN users u ON u.user_id=p.uploaded_by WHERE p.package_id=?
                """, packageId);
        Long uploadId = ((Number) packageRow.get("upload_id")).longValue();
        List<DataUploadSheetSummaryResponse> summaries = jdbcTemplate.query("""
                SELECT sheet_name, COUNT(*) total,
                    SUM(CASE WHEN row_status='ERROR' THEN 1 ELSE 0 END) errors,
                    SUM(CASE WHEN row_status='WARNING' THEN 1 ELSE 0 END) warnings
                FROM data_upload_rows WHERE review_package_id=? GROUP BY sheet_name
                """, (rs, rowNum) -> DataUploadSheetSummaryResponse.builder()
                .sheetName(rs.getString("sheet_name")).totalRows(rs.getInt("total"))
                .validRows(rs.getInt("total") - rs.getInt("errors"))
                .errorRows(rs.getInt("errors")).warningRows(rs.getInt("warnings")).build(), packageId);
        return DataUploadReviewPackageResponse.builder()
                .packageId(packageId).uploadId(uploadId).versionNo(((Number) packageRow.get("version_no")).intValue())
                .fileName((String) packageRow.get("file_name")).status((String) packageRow.get("status"))
                .uploadedBy(((Number) packageRow.get("uploaded_by")).longValue())
                .uploadedByName((String) packageRow.get("uploaded_by_name"))
                .totalRows(((Number) packageRow.get("total_rows")).intValue())
                .validRows(((Number) packageRow.get("valid_rows")).intValue())
                .errorRows(((Number) packageRow.get("error_rows")).intValue())
                .warningRows(((Number) packageRow.get("warning_rows")).intValue())
                .createdAt(toLocalDateTime(packageRow.get("created_at")))
                .validationErrors(fromJsonList((String) packageRow.get("validation_message")))
                .diffSummary(fromJsonObject((String) packageRow.get("diff_json"))).sheetSummaries(summaries).build();
    }

    private int nextPackageVersion(Long uploadId) {
        Integer version = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(version_no),0)+1 FROM data_upload_review_packages WHERE upload_id=?", Integer.class, uploadId);
        return version == null ? 1 : version;
    }

    private StoredUpload storeMultipart(MultipartFile file, String prefix) {
        if (file == null || file.isEmpty()) throw new BusinessException("请选择需要上传的 Excel 文件");
        if (file.getSize() > storageProperties.getMaxFileSize().toBytes()) throw new BusinessException("上传文件超过系统限制");
        String fileName = safeFileName(file.getOriginalFilename());
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) throw new BusinessException("仅支持无宏 .xlsx 文件");
        Path root = prepareStorage();
        Path path = root.resolve(prefix + "-" + UUID.randomUUID() + ".xlsx").normalize();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(file.getInputStream(), digest);
                 OutputStream output = Files.newOutputStream(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                input.transferTo(output);
            }
            registerRollbackCleanup(path);
            return new StoredUpload(path, fileName, HexFormat.of().formatHex(digest.digest()));
        } catch (IOException | NoSuchAlgorithmException exception) {
            deleteQuietly(path);
            throw new BusinessException("保存上传文件失败");
        }
    }

    private Path storeBytes(byte[] bytes, String prefix) {
        Path path = prepareStorage().resolve(prefix + "-" + UUID.randomUUID() + ".xlsx").normalize();
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            registerRollbackCleanup(path);
            return path;
        } catch (IOException exception) {
            deleteQuietly(path);
            throw new BusinessException("保存系统生成工作簿失败");
        }
    }

    private Path prepareStorage() {
        Path root = storageProperties.normalizedUploadDir();
        try { Files.createDirectories(root); } catch (IOException exception) { throw new BusinessException("上传目录不可写"); }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(root)) throw new BusinessException("上传目录不可写");
        return root;
    }

    private void registerRollbackCleanup(Path path) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(path);
            }
        });
    }

    private void deleteQuietly(Path path) { try { if (path != null) Files.deleteIfExists(path); } catch (IOException ignored) { } }
    private String safeFileName(String raw) {
        String value = Optional.ofNullable(raw).orElse("投稿数据.xlsx").replace('\\', '/');
        value = value.substring(value.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (value.isBlank() || value.length() > 255) throw new BusinessException("上传文件名无效");
        return value;
    }

    private BatchState findBatch(Long uploadId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM data_upload_batches WHERE upload_id=?", (rs, rowNum) ->
                    new BatchState(rs.getLong("upload_id"), rs.getLong("uploaded_by"), rs.getString("status"),
                            rs.getInt("current_revision_no"), (Long) rs.getObject("current_package_id"),
                            (Long) rs.getObject("published_release_id")), uploadId);
        } catch (EmptyResultDataAccessException exception) { throw new BusinessException(404, "上传批次不存在"); }
    }

    private BatchState lockBatch(Long uploadId) {
        try {
            return jdbcTemplate.queryForObject("SELECT * FROM data_upload_batches WHERE upload_id=? FOR UPDATE", (rs, rowNum) ->
                    new BatchState(rs.getLong("upload_id"), rs.getLong("uploaded_by"), rs.getString("status"),
                            rs.getInt("current_revision_no"), (Long) rs.getObject("current_package_id"),
                            (Long) rs.getObject("published_release_id")), uploadId);
        } catch (EmptyResultDataAccessException exception) { throw new BusinessException(404, "上传批次不存在"); }
    }

    private long insertAndReturnKey(String sql, Object... args) {
        KeyHolder holder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, holder);
        Number key = holder.getKey();
        if (key == null && !holder.getKeyList().isEmpty()) {
            key = holder.getKeyList().getFirst().values().stream().filter(Number.class::isInstance).map(Number.class::cast).findFirst().orElse(null);
        }
        if (key == null) throw new BusinessException("数据库未返回新增记录ID");
        return key.longValue();
    }

    private void insertAudit(Long uploadId, Long packageId, String action, Long actor, String from, String to,
                             String note, Object detail) {
        jdbcTemplate.update("""
                INSERT INTO data_upload_audit_events (upload_id, package_id, action, actor_id, from_status, to_status, note, detail_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, uploadId, packageId, action, actor, from, to, note, detail == null ? null : toJson(detail));
    }

    private List<String> flattenedSubmissionErrors(SimplifiedUploadWorkbook.ParsedSubmission parsed) {
        List<String> result = new ArrayList<>(parsed.workbookErrors());
        for (var row : parsed.rows()) for (String error : row.errors()) result.add("第" + row.excelRowNumber() + "行：" + error);
        return result;
    }
    private List<String> flattenedReviewErrors(SimplifiedUploadWorkbook.ParsedReview parsed) {
        List<String> result = new ArrayList<>(parsed.workbookErrors());
        for (var rows : parsed.rowsBySheet().values()) for (var row : rows) for (String error : row.errors()) result.add(row.sheetName() + "第" + row.excelRowNumber() + "行：" + error);
        return result;
    }
    private void requireFields(SimplifiedUploadWorkbook.ReviewRow row, List<String> fields) {
        for (String field : fields) if (value(row.values(), field).isBlank()) row.errors().add(field + "不能为空");
    }
    private String summarizeErrors(List<String> errors) {
        if (errors == null || errors.isEmpty()) return null;
        String joined = String.join("；", errors.stream().limit(5).toList());
        if (errors.size() > 5) joined += "；另有" + (errors.size() - 5) + "项";
        return joined.length() <= 500 ? joined : joined.substring(0, 500);
    }
    private String requiredReason(String reason) {
        String value = reason == null ? "" : reason.trim();
        if (value.isBlank()) throw new BusinessException("请填写原因");
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
    private boolean isAdmin(User user) { return user != null && "admin".equals(user.getRole()); }
    private boolean isDecimal(String value) { try { new BigDecimal(value); return true; } catch (RuntimeException ignored) { return false; } }
    private String value(Map<String, String> data, String key) { return data == null || data.get(key) == null ? "" : data.get(key).trim(); }
    private String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private String toJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (IOException exception) { throw new BusinessException("数据序列化失败"); } }
    private Map<String, String> fromJsonMap(String json) { try { return objectMapper.readValue(json, new TypeReference<>() { }); } catch (IOException exception) { throw new BusinessException("投稿数据损坏"); } }
    private List<String> fromJsonList(String json) { if (json == null || json.isBlank()) return new ArrayList<>(); try { return objectMapper.readValue(json, new TypeReference<>() { }); } catch (IOException exception) { return new ArrayList<>(); } }
    private Map<String, Object> fromJsonObject(String json) { if (json == null || json.isBlank()) return new LinkedHashMap<>(); try { return objectMapper.readValue(json, new TypeReference<>() { }); } catch (IOException exception) { return new LinkedHashMap<>(); } }
    private LocalDateTime toLocalDateTime(Object value) { return value instanceof java.sql.Timestamp timestamp ? timestamp.toLocalDateTime() : null; }

    private record StoredUpload(Path path, String fileName, String sha256) { }
    private record BatchState(Long uploadId, Long uploadedBy, String status, int currentRevisionNo,
                              Long currentPackageId, Long publishedReleaseId) { }
}
