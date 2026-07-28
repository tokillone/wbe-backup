package com.licong.webbackup.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.config.DataUploadStorageProperties;
import com.licong.webbackup.dto.upload.DataUploadBatchPageResponse;
import com.licong.webbackup.dto.upload.DataUploadBatchResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadRowResponse;
import com.licong.webbackup.dto.upload.DataUploadRowsPageResponse;
import com.licong.webbackup.dto.upload.DataUploadSheetSummaryResponse;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.util.IOUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DataUploadService {

    private static final String DATA_SHEET_NAME = "数据表";
    private static final String SITE_SHEET_NAME = "点位关联表";
    private static final String ICD11_SHEET_NAME = "药物疾病ICD11映射";
    private static final String LITERATURE_SHEET_NAME = "文献基础信息";
    private static final List<String> REQUIRED_SHEETS = List.of(
            DATA_SHEET_NAME,
            SITE_SHEET_NAME,
            ICD11_SHEET_NAME,
            LITERATURE_SHEET_NAME
    );
    private static final int PREVIEW_LIMIT = 20;
    private static final String STATUS_VALIDATION_FAILED = "VALIDATION_FAILED";
    private static final String STATUS_PENDING_REVIEW = "PENDING_REVIEW";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String STATUS_SYNCED = "SYNCED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String ROW_STATUS_ERROR = "ERROR";
    private static final String ROW_STATUS_WARNING = "WARNING";
    private static final String ROW_STATUS_VALID = "VALID";
    private static final String ROW_STATUS_SYNCED = "SYNCED";
    private static final Set<String> PRESCRIPTION_TYPES = Set.of("处方药", "非处方药", "其他");
    private static final Pattern PREFERRED_NUMBER = Pattern.compile("取\\s*([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)");
    private static final List<DateTimeFormatter> DATE_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy.M.d HH:mm:ss"),
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"),
            DateTimeFormatter.ofPattern("yyyy.M.d HH:mm")
    );
    private static final List<DateTimeFormatter> DATE_FORMATTERS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy/M/dd"),
            DateTimeFormatter.ofPattern("yyyy/MM/d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy.M.dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd")
    );
    private static final List<DateTimeFormatter> YEAR_MONTH_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM"),
            DateTimeFormatter.ofPattern("yyyy/M"),
            DateTimeFormatter.ofPattern("yyyy/MM"),
            DateTimeFormatter.ofPattern("yyyy.M"),
            DateTimeFormatter.ofPattern("yyyy.MM")
    );

    public static final List<String> REQUIRED_HEADERS = List.of(
            "文献编号",
            "目标类别",
            "目标物质类别",
            "目标物质子类",
            "目标物质细类",
            "药物",
            "适应症",
            "处方/非处方",
            "生物标记物名称",
            "biomarker",
            "生物标记物CAS",
            "理化性质",
            "校准系数",
            "采样方法",
            "分析方法",
            "MDL_value",
            "MDL_unit",
            "MQL_value",
            "MQL_unit",
            "IDL_value",
            "IDL_unit",
            "IQL_value",
            "IQL_unit",
            "污水厂名称",
            "污水厂处理规模（m3/day）",
            "汇水区人群数量",
            "污水厂位置_国",
            "污水厂位置_省",
            "污水厂位置_市",
            "样品采集时间",
            "采样开始时间_YYYY_MM",
            "采样结束时间_YYYY_MM",
            "做图浓度_value",
            "做图浓度_unit",
            "进水浓度min_value",
            "进水浓度min_unit",
            "进水浓度max_value",
            "进水浓度max_unit",
            "进水浓度average_value",
            "进水浓度average_unit",
            "进水浓度median_value",
            "进水浓度median_unit",
            "每日质量负荷DLs",
            "DLs_unit",
            "PNDL_value",
            "PNDL_unit",
            "PNDL估算_value",
            "PNDL估算_unit",
            "做图PNDL_value",
            "做图PNDL_unit",
            "GS管道衰减系数",
            "人体排泄率（%）",
            "药物消费量_value",
            "药物消费量_unit",
            "药物使用流行率（%）",
            "疾病患病率（%）",
            "DOI",
            "keywords",
            "abstract"
    );
    public static final List<String> OPTIONAL_HEADERS = List.of(
            "来源工作簿说明",
            "原表行号说明",
            "采样点编号",
            "污水厂详细地址",
            "污水厂纬度",
            "污水厂经度",
            "confirmed_site_id",
            "点位确认依据"
    );
    public static final List<String> ICD11_HEADERS = List.of(
            "目标类别", "目标物质类别", "目标物质子类", "目标物质细类", "药物", "适应症原文",
            "生物标记物名称", "biomarker", "规范适应症短语", "疾病实体短语",
            "ICD11_Level1_Code", "ICD11_Level1_Name", "ICD11_Level2_Code", "ICD11_Level2_Name",
            "ICD11_Level3_Code", "ICD11_Level3_Name", "映射层级", "匹配类型", "是否进入桑基图",
            "不入图原因", "复核状态", "备注", "生物标记物CAS", "涉及文献数", "数据行数",
            "涉及文献编号", "涉及DOI", "唯一DOI数", "DOI缺失数"
    );
    public static final List<String> LITERATURE_HEADERS = List.of(
            "文献编号", "文献名", "DOI", "keywords", "abstract"
    );
    public static final List<String> SITE_HEADERS = List.of(
            "文献编号", "DOI", "国家", "省/州", "市", "原始污水厂名称", "规范污水厂名称",
            "reported_site_key", "confirmed_site_id", "是否计入点位数", "点位说明", "同一污水厂确认依据"
    );
    private static final List<String> LEGACY_REQUIRED_HEADERS = REQUIRED_HEADERS.stream()
            .filter(header -> !"目标物质细类".equals(header))
            .toList();
    private static final List<String> LEGACY_ICD11_HEADERS = ICD11_HEADERS.stream()
            .filter(header -> !"目标物质细类".equals(header))
            .toList();

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;
    private final DataUploadStorageProperties storageProperties;

    public DataUploadService(JdbcTemplate jdbcTemplate,
                             ObjectMapper objectMapper,
                             DataSource dataSource,
                             DataUploadStorageProperties storageProperties) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
        this.storageProperties = storageProperties;
    }

    public void requireCanUpload(User user) {
        if (!canUpload(user)) {
            throw new BusinessException(403, "当前账号无权上传数据");
        }
    }

    public void requireCanReviewUploads(User user) {
        if (!canReviewUploads(user)) {
            throw new BusinessException(403, "当前账号无权审核上传批次");
        }
    }

    public void requireCanSyncData(User user) {
        if (!canSyncData(user)) {
            throw new BusinessException(403, "当前账号无权同步数据入库");
        }
    }

    public void requireCanViewUploads(User user) {
        if (!canUpload(user) && !canReviewUploads(user) && !canSyncData(user)) {
            throw new BusinessException(403, "当前账号无权查看上传批次");
        }
    }

    private boolean isAdmin(User user) {
        return user != null && "admin".equals(user.getRole());
    }

    private boolean canUpload(User user) {
        return isAdmin(user) || (user != null && Boolean.TRUE.equals(user.getCanUpload()));
    }

    private boolean canReviewUploads(User user) {
        return isAdmin(user) || (user != null && Boolean.TRUE.equals(user.getCanReviewUploads()));
    }

    private boolean canSyncData(User user) {
        return isAdmin(user) || (user != null && Boolean.TRUE.equals(user.getCanSyncData()));
    }

    private boolean canViewAllUploads(User user) {
        return isAdmin(user) || canReviewUploads(user) || canSyncData(user);
    }

    public byte[] createTemplateWorkbook() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.CellStyle titleStyle = createTemplateStyle(
                    workbook,
                    IndexedColors.DARK_TEAL,
                    IndexedColors.WHITE,
                    true,
                    HorizontalAlignment.CENTER
            );
            org.apache.poi.ss.usermodel.CellStyle headerStyle = createTemplateStyle(
                    workbook,
                    IndexedColors.LIGHT_TURQUOISE,
                    IndexedColors.DARK_TEAL,
                    true,
                    HorizontalAlignment.CENTER
            );
            org.apache.poi.ss.usermodel.CellStyle bodyStyle = createTemplateStyle(
                    workbook,
                    IndexedColors.WHITE,
                    IndexedColors.BLACK,
                    false,
                    HorizontalAlignment.LEFT
            );

            buildDataTemplateSheet(workbook, headerStyle);
            buildWorkbookSheetTemplate(workbook, SITE_SHEET_NAME, SITE_HEADERS, headerStyle);
            buildWorkbookSheetTemplate(workbook, ICD11_SHEET_NAME, ICD11_HEADERS, headerStyle);
            buildWorkbookSheetTemplate(workbook, LITERATURE_SHEET_NAME, LITERATURE_HEADERS, headerStyle);
            buildFieldGuideSheet(workbook, titleStyle, headerStyle, bodyStyle);
            buildInstructionSheet(workbook, titleStyle, headerStyle, bodyStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成 Excel 模板失败");
        }
    }

    @Transactional
    public DataUploadPreviewResponse preview(MultipartFile file, User user, boolean allowDuplicate) {
        requireCanUpload(user);
        String fileName = validateAndNormalizeOriginalFileName(file);
        StagedFile stagedFile = stageUpload(file);
        String sha256 = stagedFile.sha256();
        String duplicateMessage;
        try {
            duplicateMessage = findDuplicateMessage(sha256).orElse(null);
            if (duplicateMessage != null && !allowDuplicate) {
                throw new BusinessException("检测到相同 SHA256 的历史上传文件，已阻止重复上传；如确需重复导入，请联系系统管理员确认放行");
            }
            if (duplicateMessage != null && allowDuplicate && !isAdmin(user)) {
                throw new BusinessException(403, "只有系统管理员可以放行重复上传文件");
            }
        } catch (RuntimeException ex) {
            deleteStagedFile(stagedFile);
            throw ex;
        }

        ParsedWorkbook parsedWorkbook;
        try {
            parsedWorkbook = parseWorkbook(stagedFile.path());
        } catch (BusinessException ex) {
            deleteStagedFile(stagedFile);
            String failureMessage = truncate("文件解析失败：" + ex.getMessage(), 500);
            Long uploadId = insertBatch(
                    fileName,
                    null,
                    sha256,
                    user.getUserId(),
                    STATUS_VALIDATION_FAILED,
                    0,
                    0,
                    1,
                    0,
                    failureMessage
            );
            List<String> batchWarnings = new ArrayList<>();
            if (duplicateMessage != null) {
                batchWarnings.add(duplicateMessage);
            }
            batchWarnings.add(failureMessage);
            return DataUploadPreviewResponse.builder()
                    .batch(getBatch(uploadId))
                    .requiredHeaders(REQUIRED_HEADERS)
                    .optionalHeaders(OPTIONAL_HEADERS)
                    .headerErrors(List.of(failureMessage))
                    .batchWarnings(batchWarnings)
                    .previewRows(List.of())
                    .sheetSummaries(List.of())
                    .previewRowsBySheet(Map.of())
                    .build();
        }
        StoredFile storedFile = promoteStagedFile(stagedFile);
        List<ParsedRow> rows = parsedWorkbook.rows();
        int errorRows = (int) rows.stream().filter(ParsedRow::hasErrors).count();
        int warningRows = (int) rows.stream().filter(ParsedRow::hasWarnings).count();
        int validRows = rows.size() - errorRows;
        String status;
        if (!parsedWorkbook.headerErrors().isEmpty() || errorRows > 0) {
            status = STATUS_VALIDATION_FAILED;
        } else {
            status = STATUS_PENDING_REVIEW;
        }
        List<String> batchWarnings = new ArrayList<>();
        if (duplicateMessage != null) {
            batchWarnings.add(duplicateMessage);
        }

        Long uploadId = insertBatch(
                fileName,
                storedFile.path().toString(),
                sha256,
                user.getUserId(),
                status,
                rows.size(),
                validRows,
                errorRows,
                warningRows,
                duplicateMessage
        );
        List<DataUploadRowResponse> previewRows = new ArrayList<>();
        Map<String, List<DataUploadRowResponse>> previewRowsBySheet = new LinkedHashMap<>();
        for (String sheetName : REQUIRED_SHEETS) {
            previewRowsBySheet.put(sheetName, new ArrayList<>());
        }
        for (ParsedRow row : rows) {
            Long rowId = insertUploadRow(uploadId, row);
            DataUploadRowResponse response = toRowResponse(rowId, row, null);
            List<DataUploadRowResponse> sheetPreview = previewRowsBySheet.get(row.sheetName());
            if (sheetPreview.size() < PREVIEW_LIMIT) {
                sheetPreview.add(response);
            }
            if (previewRows.size() < PREVIEW_LIMIT) {
                previewRows.add(response);
            }
        }

        List<DataUploadSheetSummaryResponse> sheetSummaries = REQUIRED_SHEETS.stream()
                .map(sheetName -> {
                    List<ParsedRow> sheetRows = rows.stream()
                            .filter(row -> sheetName.equals(row.sheetName()))
                            .toList();
                    int sheetErrors = (int) sheetRows.stream().filter(ParsedRow::hasErrors).count();
                    int sheetWarnings = (int) sheetRows.stream().filter(ParsedRow::hasWarnings).count();
                    return DataUploadSheetSummaryResponse.builder()
                            .sheetName(sheetName)
                            .totalRows(sheetRows.size())
                            .validRows(sheetRows.size() - sheetErrors)
                            .warningRows(sheetWarnings)
                            .errorRows(sheetErrors)
                            .build();
                })
                .toList();

        DataUploadBatchResponse batch = getBatch(uploadId);
        return DataUploadPreviewResponse.builder()
                .batch(batch)
                .requiredHeaders(REQUIRED_HEADERS)
                .optionalHeaders(OPTIONAL_HEADERS)
                .headerErrors(parsedWorkbook.headerErrors())
                .batchWarnings(batchWarnings)
                .previewRows(previewRows)
                .sheetSummaries(sheetSummaries)
                .previewRowsBySheet(previewRowsBySheet)
                .build();
    }

    public DataUploadBatchPageResponse listBatches(
            User user,
            int page,
            int size,
            String keyword,
            String status,
            String scope,
            String uploaderType,
            String sort) {
        requireCanViewUploads(user);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int normalizedPage = Math.max(1, page);
        int offset = (normalizedPage - 1) * normalizedSize;
        boolean canSeeAll = canViewAllUploads(user);

        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (!canSeeAll) {
            where.append(" AND b.uploaded_by = ?");
            args.add(user.getUserId());
        } else {
            String normalizedScope = normalizeFilter(scope);
            if ("mine".equals(normalizedScope)) {
                where.append(" AND b.uploaded_by = ?");
                args.add(user.getUserId());
            } else if ("pendingReview".equals(normalizedScope)) {
                where.append(" AND b.status = ?");
                args.add(STATUS_PENDING_REVIEW);
            } else if ("approved".equals(normalizedScope)) {
                where.append(" AND b.status = ?");
                args.add(STATUS_APPROVED);
            }

            String normalizedUploaderType = normalizeFilter(uploaderType);
            if ("viewer".equals(normalizedUploaderType)) {
                where.append(" AND u.role = 'viewer'");
            } else if ("manager".equals(normalizedUploaderType)) {
                where.append(" AND u.role IN ('admin', 'editor')");
            }
        }

        String normalizedStatus = normalizeFilter(status);
        if (normalizedStatus != null && !"all".equals(normalizedStatus)) {
            where.append(" AND b.status = ?");
            args.add(normalizedStatus);
        }

        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (!normalizedKeyword.isBlank()) {
            String like = "%" + normalizedKeyword + "%";
            where.append("""
                     AND (
                        LOWER(b.file_name) LIKE ?
                        OR LOWER(u.username) LIKE ?
                        OR LOWER(u.email) LIKE ?
                        OR LOWER(b.status) LIKE ?
                        OR LOWER(COALESCE(b.duplicate_message, '')) LIKE ?
                     )
                    """);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
            args.add(like);
        }

        String orderBy = " ORDER BY b.created_at DESC, b.upload_id DESC";
        if ("createdAt_asc".equals(sort)) {
            orderBy = " ORDER BY b.created_at ASC, b.upload_id ASC";
        }

        String fromSql = """
                FROM data_upload_batches b
                JOIN users u ON u.user_id = b.uploaded_by
                LEFT JOIN users reviewer ON reviewer.user_id = b.reviewed_by
                LEFT JOIN users syncer ON syncer.user_id = b.synced_by
                """;
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) " + fromSql + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(normalizedSize);
        pageArgs.add(offset);
        List<DataUploadBatchResponse> items = jdbcTemplate.query(
                batchSelectSql() + fromSql + where + orderBy + " LIMIT ? OFFSET ?",
                batchMapper(),
                pageArgs.toArray()
        );
        long safeTotal = total == null ? 0L : total;
        int totalPages = safeTotal == 0 ? 0 : (int) Math.ceil((double) safeTotal / normalizedSize);
        return DataUploadBatchPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .total(safeTotal)
                .totalPages(totalPages)
                .build();
    }

    public DataUploadRowsPageResponse listRows(Long uploadId, int page, int size, String status, User user) {
        requireCanViewUploads(user);
        ensureBatchAccess(findBatchRecord(uploadId), user);
        int normalizedSize = Math.max(1, Math.min(size, 100));
        int normalizedPage = Math.max(1, page);
        int offset = (normalizedPage - 1) * normalizedSize;
        String normalizedStatus = normalizeFilter(status);
        boolean hasStatusFilter = normalizedStatus != null && !"all".equals(normalizedStatus);
        List<Object> args = new ArrayList<>();
        args.add(uploadId);
        String where = "WHERE upload_id = ?";
        if (hasStatusFilter) {
            where += " AND row_status = ?";
            args.add(normalizedStatus);
        }
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_upload_rows " + where,
                Long.class,
                args.toArray()
        );
        List<Object> rowArgs = new ArrayList<>(args);
        rowArgs.add(normalizedSize);
        rowArgs.add(offset);
        List<DataUploadRowResponse> rows = jdbcTemplate.query("""
                        SELECT *
                        FROM data_upload_rows
                        %s
                        ORDER BY FIELD(sheet_name, '数据表', '药物疾病ICD11映射', '文献基础信息'),
                                 excel_row_number ASC
                        LIMIT ? OFFSET ?
                        """.formatted(where),
                rowMapper(),
                rowArgs.toArray()
        );
        return DataUploadRowsPageResponse.builder()
                .uploadId(uploadId)
                .page(normalizedPage)
                .size(normalizedSize)
                .total(total == null ? 0L : total)
                .rows(rows)
                .build();
    }

    public Path getStoredFile(Long uploadId, User user) {
        requireCanViewUploads(user);
        if (!isAdmin(user) && Boolean.FALSE.equals(user.getCanDownload())) {
            throw new BusinessException(403, "当前账号已被禁止下载文件");
        }
        BatchRecord batch = findBatchRecord(uploadId);
        ensureBatchAccess(batch, user);
        if (batch.storedFilePath() == null || batch.storedFilePath().isBlank()) {
            throw new BusinessException("原始文件不存在");
        }
        Path path = resolveStoredFilePath(batch.storedFilePath());
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("原始文件已被移除");
        }
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException("原始文件路径无效");
        }
        ensureRealPathWithinManagedRoots(path);
        return path;
    }

    public String getFileName(Long uploadId, User user) {
        requireCanViewUploads(user);
        if (!isAdmin(user) && Boolean.FALSE.equals(user.getCanDownload())) {
            throw new BusinessException(403, "当前账号已被禁止下载文件");
        }
        BatchRecord batch = findBatchRecord(uploadId);
        ensureBatchAccess(batch, user);
        return safeDownloadFileName(batch.fileName());
    }

    @Transactional
    public DataUploadBatchResponse approve(Long uploadId, User user) {
        requireCanReviewUploads(user);
        BatchRecord batch = findBatchRecordForUpdate(uploadId);
        if (!STATUS_PENDING_REVIEW.equals(batch.status())) {
            if (STATUS_APPROVED.equals(batch.status())) {
                throw new BusinessException("该批次已经审核通过");
            }
            if (STATUS_SYNCED.equals(batch.status())) {
                throw new BusinessException("已入库批次不能重复审核");
            }
            if (STATUS_REJECTED.equals(batch.status())) {
                throw new BusinessException("该批次已经驳回");
            }
            throw new BusinessException("只有待审核批次可以审核通过");
        }
        jdbcTemplate.update("""
                        UPDATE data_upload_batches
                        SET status = ?,
                            reviewed_by = ?,
                            reviewed_at = NOW(),
                            review_action = ?,
                            review_note = NULL
                        WHERE upload_id = ?
                        """,
                STATUS_APPROVED,
                user.getUserId(),
                STATUS_APPROVED,
                uploadId
        );
        return getBatch(uploadId);
    }

    @Transactional
    public DataUploadSyncResponse sync(Long uploadId, User user) {
        requireCanSyncData(user);
        BatchRecord batch = findBatchRecordForUpdate(uploadId);
        ensureBatchAccess(batch, user);
        if (!STATUS_APPROVED.equals(batch.status())) {
            if (STATUS_SYNCED.equals(batch.status())) {
                throw new BusinessException("该批次已经同步入库");
            }
            if (STATUS_PENDING_REVIEW.equals(batch.status())) {
                throw new BusinessException("该批次尚未审核通过，不能同步入库");
            }
            throw new BusinessException("该批次当前状态不能同步入库");
        }

        List<DataUploadRowResponse> rows = jdbcTemplate.query("""
                        SELECT *
                        FROM data_upload_rows
                        WHERE upload_id = ? AND row_status <> ?
                        ORDER BY CASE sheet_name
                                     WHEN '文献基础信息' THEN 1
                                     WHEN '点位关联表' THEN 2
                                     WHEN '数据表' THEN 3
                                     WHEN '药物疾病ICD11映射' THEN 4
                                     ELSE 4
                                 END,
                                 excel_row_number ASC
                        """,
                rowMapper(),
                uploadId,
                ROW_STATUS_ERROR
        );
        if (rows.size() != batchTotalRows(uploadId)) {
            throw new BusinessException("批次包含校验错误行，不能同步入库");
        }

        Map<String, List<DataUploadRowResponse>> rowsBySheet = new LinkedHashMap<>();
        for (String sheetName : REQUIRED_SHEETS) rowsBySheet.put(sheetName, new ArrayList<>());
        for (DataUploadRowResponse row : rows) {
            rowsBySheet.computeIfAbsent(row.getSheetName(), ignored -> new ArrayList<>()).add(row);
        }

        int insertedRows = 0;
        List<String> syncWarnings = new ArrayList<>();
        Map<String, Integer> insertedRowsBySheet = new LinkedHashMap<>();
        Map<String, String> literatureDois = new LinkedHashMap<>();

        try {
            clearWorkbookManagedData();

            for (DataUploadRowResponse row : rowsBySheet.get(LITERATURE_SHEET_NAME)) {
                insertLiterature(batch.uploadId(), row);
                String code = valueOrNull(row.getData().get("文献编号"));
                String doi = valueOrNull(row.getData().get("DOI"));
                if (code != null && doi != null) literatureDois.put(code, doi);
                markSynced(row, "LITERATURE", null, null);
            }
            insertedRowsBySheet.put(LITERATURE_SHEET_NAME, rowsBySheet.get(LITERATURE_SHEET_NAME).size());

            List<SiteLinkageMatcher.SiteRow> siteRows = new ArrayList<>();
            for (DataUploadRowResponse row : rowsBySheet.get(SITE_SHEET_NAME)) {
                SiteLinkageMatcher.SiteRow site = toSiteRow(row);
                upsertWorkbookReportedSite(batch.uploadId(), row, site);
                siteRows.add(site);
                markSynced(row, "REPORTED_SITE", null, null);
            }
            insertedRowsBySheet.put(SITE_SHEET_NAME, rowsBySheet.get(SITE_SHEET_NAME).size());
            Map<String, List<SiteLinkageMatcher.SiteRow>> sitesByLiterature = siteRows.stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            site -> SiteLinkageMatcher.normalized(site.literatureCode()),
                            LinkedHashMap::new,
                            java.util.stream.Collectors.toList()));

            List<SiteLinkageMatcher.MatchResult> siteMatches = new ArrayList<>();
            for (DataUploadRowResponse row : rowsBySheet.get(DATA_SHEET_NAME)) {
                Long measurementId = syncSnapshotDataRow(batch.uploadId(), row);
                String internalRecordKey = "upload:" + batch.uploadId() + ":row:" + row.getExcelRowNumber();
                SiteLinkageMatcher.MatchResult match = SiteLinkageMatcher.match(
                        row.getData(),
                        sitesByLiterature.getOrDefault(
                                SiteLinkageMatcher.normalized(row.getData().get("文献编号")), List.of()),
                        internalRecordKey);
                insertRecordSiteBridge(batch.uploadId(), row, measurementId, match);
                siteMatches.add(match);
                insertHomeTargetRecord(row, literatureDois);
                markSynced(row, "MEASUREMENT", measurementId, measurementId);
            }
            insertedRowsBySheet.put(DATA_SHEET_NAME, rowsBySheet.get(DATA_SHEET_NAME).size());
            persistSiteLinkQc(batch.uploadId(), siteRows, siteMatches);

            for (DataUploadRowResponse row : rowsBySheet.get(ICD11_SHEET_NAME)) {
                Long sankeyPathId = insertIcd11Mapping(batch.uploadId(), row);
                insertIcd11Sources(sankeyPathId, row, literatureDois);
                markSynced(row, "ICD11_SANKEY_PATH", sankeyPathId, null);
            }
            insertedRowsBySheet.put(ICD11_SHEET_NAME, rowsBySheet.get(ICD11_SHEET_NAME).size());
            insertedRows = insertedRowsBySheet.values().stream().mapToInt(Integer::intValue).sum();
        } catch (Exception ex) {
            throw new BusinessException("工作簿同步失败：" + ex.getMessage());
        }

        jdbcTemplate.update("""
                        UPDATE data_upload_batches
                        SET status = ?,
                            synced_rows = ?,
                            synced_at = NOW(),
                            synced_by = ?
                        WHERE upload_id = ?
                        """,
                STATUS_SYNCED,
                insertedRows,
                user.getUserId(),
                uploadId
        );

        tryRefreshMapStats(syncWarnings);
        return DataUploadSyncResponse.builder()
                .batch(getBatch(uploadId))
                .insertedRows(insertedRows)
                .skippedRows(0)
                .insertedRowsBySheet(insertedRowsBySheet)
                .warnings(syncWarnings)
                .build();
    }

    @Transactional
    public DataUploadBatchResponse reject(Long uploadId, User user, String reason) {
        requireCanReviewUploads(user);
        BatchRecord batch = findBatchRecordForUpdate(uploadId);
        if (!STATUS_PENDING_REVIEW.equals(batch.status())) {
            if (STATUS_SYNCED.equals(batch.status())) {
                throw new BusinessException("已入库批次不能驳回");
            }
            if (STATUS_REJECTED.equals(batch.status())) {
                throw new BusinessException("该批次已经驳回");
            }
            if (STATUS_APPROVED.equals(batch.status())) {
                throw new BusinessException("已审核通过批次不能驳回");
            }
            throw new BusinessException("只有待审核批次可以驳回");
        }
        String reviewNote = reason == null ? null : reason.trim();
        if (reviewNote != null && reviewNote.length() > 500) {
            throw new BusinessException("驳回原因不能超过 500 个字符");
        }
        if (reviewNote != null && reviewNote.isBlank()) {
            reviewNote = null;
        }
        jdbcTemplate.update("""
                        UPDATE data_upload_batches
                        SET status = ?,
                            reviewed_by = ?,
                            reviewed_at = NOW(),
                            review_action = ?,
                            review_note = ?
                        WHERE upload_id = ?
                        """,
                STATUS_REJECTED,
                user.getUserId(),
                STATUS_REJECTED,
                reviewNote,
                uploadId
        );
        return getBatch(uploadId);
    }

    private Long syncSnapshotDataRow(Long uploadId, DataUploadRowResponse row) throws IOException {
        Map<String, String> data = row.getData();
        Long compoundId = getOrCreateCompound(data);
        Long methodId = getOrCreateMethod(data);
        Long plantId = getOrCreatePlant(data);
        String dedupeKey = sha256(("wbe-snapshot-v1\u001F" + uploadId + "\u001F" + row.getRowId())
                .getBytes(StandardCharsets.UTF_8));
        Long eventId = insertSamplingEvent(data, plantId, null);
        return insertMeasurement(data, compoundId, methodId, eventId, uploadId, row.getRowId(), dedupeKey);
    }

    private int batchTotalRows(Long uploadId) {
        Integer total = jdbcTemplate.queryForObject(
                "SELECT total_rows FROM data_upload_batches WHERE upload_id = ?",
                Integer.class,
                uploadId
        );
        return total == null ? 0 : total;
    }

    private void clearWorkbookManagedData() {
        for (String table : List.of(
                "record_site_bridge",
                "map_pndl_stats",
                "icd11_sankey_path_sources",
                "icd11_sankey_paths",
                "home_target_records",
                "measurements",
                "sampling_events",
                "analytical_methods",
                "compounds",
                "wastewater_plants",
                "literatures"
        )) {
            if (tableExists(table)) jdbcTemplate.update("DELETE FROM " + table);
        }
        if (tableExists("reported_sites")) {
            jdbcTemplate.update("""
                    UPDATE reported_sites
                    SET include_in_point_count = FALSE,
                        upload_id = NULL,
                        upload_row_id = NULL,
                        excel_row_number = NULL
                    """);
        }
    }

    private SiteLinkageMatcher.SiteRow toSiteRow(DataUploadRowResponse row) {
        Map<String, String> data = row.getData();
        return new SiteLinkageMatcher.SiteRow(
                row.getExcelRowNumber(),
                data.get("文献编号"),
                data.get("DOI"),
                data.get("国家"),
                data.get("省/州"),
                data.get("市"),
                data.get("原始污水厂名称"),
                data.get("规范污水厂名称"),
                data.get("reported_site_key"),
                data.get("confirmed_site_id"),
                SiteLinkageMatcher.included(data.get("是否计入点位数")),
                data.get("点位说明"),
                data.get("同一污水厂确认依据")
        );
    }

    private void upsertWorkbookReportedSite(Long uploadId,
                                            DataUploadRowResponse row,
                                            SiteLinkageMatcher.SiteRow site) {
        String confirmedSiteId = nullIfBlank(site.confirmedSiteId());
        if (confirmedSiteId != null) {
            if (site.country().isBlank()) {
                throw new BusinessException("点位关联表中 confirmed_site_id 必须有国家：" + confirmedSiteId);
            }
            String evidence = site.confirmationEvidence().isBlank()
                    ? "点位关联表候选，尚未批准跨文献合并"
                    : site.confirmationEvidence();
            String existingCountry = queryOptionalString(
                    "SELECT country FROM confirmed_sites WHERE confirmed_site_id = ?", confirmedSiteId);
            if (existingCountry != null
                    && !SiteLinkageMatcher.normalized(existingCountry)
                    .equals(SiteLinkageMatcher.normalized(site.country()))) {
                throw new BusinessException("confirmed_site_id “" + confirmedSiteId + "”不能跨国家复用");
            }
            jdbcTemplate.update("""
                            INSERT INTO confirmed_sites (
                                confirmed_site_id, canonical_name, country, province, city,
                                status, confirmation_evidence, confirmed_at
                            ) VALUES (?, ?, ?, ?, ?, 'CANDIDATE', ?, NOW())
                            ON DUPLICATE KEY UPDATE
                                canonical_name = COALESCE(NULLIF(VALUES(canonical_name), ''), canonical_name),
                                province = COALESCE(NULLIF(VALUES(province), ''), province),
                                city = COALESCE(NULLIF(VALUES(city), ''), city),
                                confirmation_evidence = COALESCE(NULLIF(VALUES(confirmation_evidence), ''), confirmation_evidence)
                            """,
                    confirmedSiteId,
                    nullIfBlank(site.canonicalPlantName()),
                    site.country(),
                    nullIfBlank(site.province()),
                    nullIfBlank(site.city()),
                    evidence);
        }

        jdbcTemplate.update("""
                        INSERT INTO reported_sites (
                            reported_site_key, literature_code, doi, raw_plant_name,
                            canonical_plant_name, country, province, city, key_quality,
                            confirmed_site_id, confirmation_evidence, include_in_point_count,
                            site_note, upload_id, upload_row_id, excel_row_number
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'WORKBOOK', ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            literature_code = VALUES(literature_code),
                            doi = VALUES(doi),
                            raw_plant_name = VALUES(raw_plant_name),
                            canonical_plant_name = VALUES(canonical_plant_name),
                            country = VALUES(country),
                            province = VALUES(province),
                            city = VALUES(city),
                            key_quality = VALUES(key_quality),
                            confirmed_site_id = VALUES(confirmed_site_id),
                            confirmation_evidence = VALUES(confirmation_evidence),
                            include_in_point_count = VALUES(include_in_point_count),
                            site_note = VALUES(site_note),
                            upload_id = VALUES(upload_id),
                            upload_row_id = VALUES(upload_row_id),
                            excel_row_number = VALUES(excel_row_number)
                        """,
                site.reportedSiteKey(),
                site.literatureCode(),
                nullIfBlank(site.doi()),
                nullIfBlank(site.rawPlantName()),
                nullIfBlank(site.canonicalPlantName()),
                nullIfBlank(site.country()),
                nullIfBlank(site.province()),
                nullIfBlank(site.city()),
                confirmedSiteId,
                nullIfBlank(site.confirmationEvidence()),
                site.includeInPointCount(),
                nullIfBlank(site.siteNote()),
                uploadId,
                row.getRowId(),
                row.getExcelRowNumber());
    }

    private void insertRecordSiteBridge(Long uploadId,
                                        DataUploadRowResponse row,
                                        Long measurementId,
                                        SiteLinkageMatcher.MatchResult match) {
        if (match.reportedSiteKeys().isEmpty()) {
            insertRecordSiteBridgeRow(uploadId, row, measurementId, match, null, null);
            return;
        }
        Map<String, String> effectiveByReported = new LinkedHashMap<>();
        for (String effective : match.effectiveSiteKeys()) {
            if (effective.startsWith("reported:")) {
                effectiveByReported.put(effective.substring("reported:".length()), effective);
            }
        }
        for (String reportedSiteKey : match.reportedSiteKeys()) {
            insertRecordSiteBridgeRow(uploadId, row, measurementId, match,
                    reportedSiteKey, effectiveByReported.get(reportedSiteKey));
        }
    }

    private void insertRecordSiteBridgeRow(Long uploadId,
                                           DataUploadRowResponse row,
                                           Long measurementId,
                                           SiteLinkageMatcher.MatchResult match,
                                           String reportedSiteKey,
                                           String effectiveSiteKey) {
        jdbcTemplate.update("""
                        INSERT INTO record_site_bridge (
                            upload_id, upload_row_id, excel_row_number, internal_record_key,
                            measurement_id, reported_site_key, effective_site_key, match_status
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                uploadId,
                row.getRowId(),
                row.getExcelRowNumber(),
                match.internalRecordKey(),
                measurementId,
                reportedSiteKey,
                effectiveSiteKey,
                match.status());
    }

    private void persistSiteLinkQc(Long uploadId,
                                   List<SiteLinkageMatcher.SiteRow> sites,
                                   List<SiteLinkageMatcher.MatchResult> matches) {
        Map<String, Long> statusCounts = matches.stream().collect(java.util.stream.Collectors.groupingBy(
                SiteLinkageMatcher.MatchResult::status,
                LinkedHashMap::new,
                java.util.stream.Collectors.counting()));
        Set<String> mappedSiteKeys = matches.stream()
                .flatMap(match -> match.effectiveSiteKeys().stream())
                .filter(key -> key.startsWith("reported:"))
                .map(key -> key.substring("reported:".length()))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> unmappedSites = sites.stream()
                .filter(SiteLinkageMatcher.SiteRow::includeInPointCount)
                .filter(site -> !mappedSiteKeys.contains(site.reportedSiteKey()))
                .map(site -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("reportedSiteKey", site.reportedSiteKey());
                    item.put("literatureCode", site.literatureCode());
                    item.put("country", site.country());
                    item.put("province", site.province());
                    item.put("city", site.city());
                    item.put("rawPlantName", site.rawPlantName());
                    return item;
                })
                .toList();
        long includedSites = sites.stream().filter(SiteLinkageMatcher.SiteRow::includeInPointCount).count();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mergeConfirmedCrossDocumentSites", false);
        report.put("pointCountBasis", "reported_site_key");
        report.put("siteRows", sites.size());
        report.put("includedSites", includedSites);
        report.put("excludedSites", sites.size() - includedSites);
        report.put("recordRows", matches.size());
        report.put("matchStatusCounts", statusCounts);
        report.put("mappedSites", mappedSiteKeys.size());
        report.put("unmappedSites", unmappedSites);

        jdbcTemplate.update("""
                        INSERT INTO site_link_import_qc (
                            upload_id, merge_confirmed_cross_document_sites,
                            site_rows, included_sites, excluded_sites, mapped_sites, unmapped_sites,
                            record_rows, exact_records, multi_site_records, location_fallback_records,
                            excluded_records, unmatched_country_records, unmatched_records, report_json
                        ) VALUES (?, FALSE, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            merge_confirmed_cross_document_sites = FALSE,
                            site_rows = VALUES(site_rows),
                            included_sites = VALUES(included_sites),
                            excluded_sites = VALUES(excluded_sites),
                            mapped_sites = VALUES(mapped_sites),
                            unmapped_sites = VALUES(unmapped_sites),
                            record_rows = VALUES(record_rows),
                            exact_records = VALUES(exact_records),
                            multi_site_records = VALUES(multi_site_records),
                            location_fallback_records = VALUES(location_fallback_records),
                            excluded_records = VALUES(excluded_records),
                            unmatched_country_records = VALUES(unmatched_country_records),
                            unmatched_records = VALUES(unmatched_records),
                            report_json = VALUES(report_json),
                            created_at = NOW()
                        """,
                uploadId,
                sites.size(),
                includedSites,
                sites.size() - includedSites,
                mappedSiteKeys.size(),
                unmappedSites.size(),
                matches.size(),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_EXACT, 0L),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_MULTI, 0L),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_POSITION_FALLBACK, 0L),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_EXCLUDED, 0L),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_UNMATCHED_COUNTRY, 0L),
                statusCounts.getOrDefault(SiteLinkageMatcher.STATUS_UNMATCHED, 0L),
                toJson(report));
    }

    private String nullIfBlank(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void insertLiterature(Long uploadId, DataUploadRowResponse row) {
        Map<String, String> data = row.getData();
        jdbcTemplate.update("""
                        INSERT INTO literatures (
                            literature_code, title, doi, keywords, abstract,
                            upload_id, upload_row_id, raw_payload
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                valueOrFallback(data.get("文献编号"), "NA"),
                valueOrNull(data.get("文献名")),
                valueOrNull(data.get("DOI")),
                valueOrNull(data.get("keywords")),
                valueOrNull(data.get("abstract")),
                uploadId,
                row.getRowId(),
                toJson(data)
        );
    }

    private Long insertIcd11Mapping(Long uploadId, DataUploadRowResponse row) {
        Map<String, String> data = row.getData();
        BigDecimal literatureCount = parseDecimalForDatabase(data.get("涉及文献数"));
        if (literatureCount == null) literatureCount = BigDecimal.ONE;
        return insertAndReturnKey("""
                        INSERT INTO icd11_sankey_paths (
                            target_category, substance_category, substance_subclass, substance_fine,
                            drug_name, indication_original, biomarker_name, biomarker_alias,
                            normalized_indication, disease_entity,
                            icd11_level1_code, icd11_level1_name,
                            icd11_level2_code, icd11_level2_name,
                            icd11_level3_code, icd11_level3_name,
                            mapping_level, match_type, in_sankey, exclusion_reason,
                            review_status, note, biomarker_cas,
                            literature_count, data_row_count, unique_doi_count, missing_doi_count,
                            upload_id, upload_row_id, raw_payload
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                                  ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                valueOrFallback(data.get("目标类别"), "未分类"),
                valueOrFallback(data.get("目标物质类别"), "未分类"),
                valueOrFallback(data.get("目标物质子类"), "未分类"),
                valueOrNull(data.get("目标物质细类")),
                valueOrFallback(data.get("药物"), "未命名药物"),
                valueOrNull(data.get("适应症原文")),
                valueOrFallback(data.get("生物标记物名称"), "未命名生物标记物"),
                valueOrNull(data.get("biomarker")),
                valueOrNull(data.get("规范适应症短语")),
                valueOrNull(data.get("疾病实体短语")),
                valueOrNull(data.get("ICD11_Level1_Code")),
                valueOrFallback(data.get("ICD11_Level1_Name"), "未分类"),
                valueOrNull(data.get("ICD11_Level2_Code")),
                valueOrFallback(data.get("ICD11_Level2_Name"), "未分类"),
                valueOrNull(data.get("ICD11_Level3_Code")),
                valueOrNull(data.get("ICD11_Level3_Name")),
                valueOrFallback(data.get("映射层级"), "Level2"),
                valueOrNull(data.get("匹配类型")),
                "是".equals(normalizeCell(data.get("是否进入桑基图"))),
                valueOrNull(data.get("不入图原因")),
                valueOrNull(data.get("复核状态")),
                valueOrNull(data.get("备注")),
                valueOrNull(data.get("生物标记物CAS")),
                literatureCount,
                Optional.ofNullable(parseLongForDatabase(data.get("数据行数"))).orElse(0L),
                Optional.ofNullable(parseIntegerForDatabase(data.get("唯一DOI数"))).orElse(0),
                Optional.ofNullable(parseIntegerForDatabase(data.get("DOI缺失数"))).orElse(0),
                uploadId,
                row.getRowId(),
                toJson(data)
        );
    }

    private void insertIcd11Sources(Long sankeyPathId,
                                    DataUploadRowResponse row,
                                    Map<String, String> literatureDois) {
        List<String> sourceCodes = splitSourceList(row.getData().get("涉及文献编号"));
        List<String> listedDois = splitSourceList(row.getData().get("涉及DOI"));
        Set<String> consumedDois = new LinkedHashSet<>();
        int sourceOrder = 1;
        for (String code : sourceCodes) {
            String doi = literatureDois.get(code);
            if (doi != null) consumedDois.add(normalizeDoi(doi));
            insertIcd11Source(sankeyPathId, sourceOrder++, code, doi);
        }
        for (String doi : listedDois) {
            if (consumedDois.add(normalizeDoi(doi))) {
                insertIcd11Source(sankeyPathId, sourceOrder++, null, doi);
            }
        }
    }

    private void insertIcd11Source(Long sankeyPathId,
                                   int sourceOrder,
                                   String literatureCode,
                                   String doi) {
        jdbcTemplate.update("""
                        INSERT INTO icd11_sankey_path_sources (
                            sankey_path_id, source_order, literature_code, doi
                        ) VALUES (?, ?, ?, ?)
                        """,
                sankeyPathId,
                sourceOrder,
                literatureCode,
                valueOrNull(doi)
        );
    }

    private void insertHomeTargetRecord(DataUploadRowResponse row, Map<String, String> literatureDois) {
        Map<String, String> data = row.getData();
        String literatureCode = valueOrNull(data.get("文献编号"));
        String doi = valueOrNull(data.get("DOI"));
        if (doi == null && literatureCode != null) doi = literatureDois.get(literatureCode);
        String targetCategory = valueOrNull(data.get("目标类别"));
        String substanceCategory = valueOrNull(data.get("目标物质类别"));
        String biomarkerName = firstUseful(data.get("生物标记物名称"), data.get("biomarker"), data.get("药物"));
        if (literatureCode == null || doi == null || targetCategory == null
                || substanceCategory == null || !isUseful(biomarkerName)) return;
        jdbcTemplate.update("""
                        INSERT INTO home_target_records (
                            literature_id, doi, target_category, target_group,
                            substance_category, substance_subclass, substance_fine, biomarker_name,
                            source_sheet, source_row_number
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                literatureCode,
                doi,
                targetCategory,
                targetCategory.contains("药物") ? "drug" : "consumer",
                substanceCategory,
                valueOrFallback(data.get("目标物质子类"), "默认"),
                valueOrNull(data.get("目标物质细类")),
                biomarkerName,
                DATA_SHEET_NAME,
                row.getExcelRowNumber()
        );
    }

    private void markSynced(DataUploadRowResponse row,
                            String entityType,
                            Long entityId,
                            Long measurementId) {
        jdbcTemplate.update("""
                        UPDATE data_upload_rows
                        SET row_status = ?,
                            synced_entity_type = ?,
                            synced_entity_id = ?,
                            synced_measurement_id = ?
                        WHERE row_id = ?
                        """,
                ROW_STATUS_SYNCED,
                entityType,
                entityId,
                measurementId,
                row.getRowId()
        );
    }

    private void buildDataTemplateSheet(Workbook workbook, org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(DATA_SHEET_NAME);
        Row header = sheet.createRow(0);
        List<String> headers = new ArrayList<>(REQUIRED_HEADERS);
        headers.addAll(OPTIONAL_HEADERS);
        for (int i = 0; i < headers.size(); i++) {
            setCell(header, i, headers.get(i), headerStyle);
            int width = Math.max(14, Math.min(headers.get(i).length() + 6, 32));
            sheet.setColumnWidth(i, width * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.size() - 1));
    }

    private void buildWorkbookSheetTemplate(Workbook workbook,
                                            String sheetName,
                                            List<String> headers,
                                            org.apache.poi.ss.usermodel.CellStyle headerStyle) {
        Sheet sheet = workbook.createSheet(sheetName);
        Row header = sheet.createRow(0);
        for (int index = 0; index < headers.size(); index++) {
            setCell(header, index, headers.get(index), headerStyle);
            int width = Math.max(14, Math.min(headers.get(index).length() + 6, 48));
            sheet.setColumnWidth(index, width * 256);
        }
        sheet.createFreezePane(0, 1);
        sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, headers.size() - 1));
    }

    private void buildFieldGuideSheet(Workbook workbook,
                                      org.apache.poi.ss.usermodel.CellStyle titleStyle,
                                      org.apache.poi.ss.usermodel.CellStyle headerStyle,
                                      org.apache.poi.ss.usermodel.CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("字段说明");
        Row title = sheet.createRow(0);
        title.setHeightInPoints(26);
        setCell(title, 0, "WBE 数据上传字段清单", titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 3));

        Row header = sheet.createRow(2);
        setCell(header, 0, "字段名", headerStyle);
        setCell(header, 1, "字段类型", headerStyle);
        setCell(header, 2, "入库位置", headerStyle);
        setCell(header, 3, "填写说明", headerStyle);

        int rowIndex = 3;
        for (String field : REQUIRED_HEADERS) {
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, field, bodyStyle);
            setCell(row, 1, "必需表头", bodyStyle);
            setCell(row, 2, targetTableForField(field), bodyStyle);
            setCell(row, 3, fieldGuideForField(field), bodyStyle);
        }
        for (String field : OPTIONAL_HEADERS) {
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, field, bodyStyle);
            setCell(row, 1, "可选追踪列", bodyStyle);
            setCell(row, 2, targetTableForOptionalField(field), bodyStyle);
            setCell(row, 3, optionalFieldGuide(field), bodyStyle);
        }

        sheet.createFreezePane(0, 3);
        sheet.setColumnWidth(0, 26 * 256);
        sheet.setColumnWidth(1, 14 * 256);
        sheet.setColumnWidth(2, 28 * 256);
        sheet.setColumnWidth(3, 52 * 256);
    }

    private void buildInstructionSheet(Workbook workbook,
                                       org.apache.poi.ss.usermodel.CellStyle titleStyle,
                                       org.apache.poi.ss.usermodel.CellStyle headerStyle,
                                       org.apache.poi.ss.usermodel.CellStyle bodyStyle) {
        Sheet sheet = workbook.createSheet("上传说明");
        Row title = sheet.createRow(0);
        title.setHeightInPoints(26);
        setCell(title, 0, "数据上传模板使用说明", titleStyle);
        sheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 2));

        List<String[]> rows = List.of(
                new String[]{"格式要求", "文件类型", "仅支持 .xlsx 文件。"},
                new String[]{"格式要求", "工作表名称", "必须保留名为“数据表”的 sheet。"},
                new String[]{"格式要求", "表头规则", "“数据表”第 1 行前 59 列必须与字段清单完全一致；不要调整顺序。"},
                new String[]{"格式要求", "可选列", "可填写采样点编号、详细地址、经纬度和人工确认点位字段；confirmed_site_id 有值时必须填写点位确认依据。"},
                new String[]{"数据规则", "NA / ND / <LOD / <LOQ", "系统会保留原始值；可入 DECIMAL 的部分入库，无法入库的数值列写入 NULL 并给出警告。"},
                new String[]{"同步规则", "审核与同步", "上传校验通过后先进入待审核队列；审核通过后，再由具备同步权限的人员入库。"},
                new String[]{"同步规则", "重复控制", "相同 SHA256 文件默认阻断；管理员显式放行后，入库时仍会按追溯字段和业务键检查重复。"}
        );

        Row header = sheet.createRow(2);
        setCell(header, 0, "类别", headerStyle);
        setCell(header, 1, "项目", headerStyle);
        setCell(header, 2, "说明", headerStyle);

        int rowIndex = 3;
        for (String[] item : rows) {
            Row row = sheet.createRow(rowIndex++);
            setCell(row, 0, item[0], bodyStyle);
            setCell(row, 1, item[1], bodyStyle);
            setCell(row, 2, item[2], bodyStyle);
        }
        sheet.createFreezePane(0, 3);
        sheet.setColumnWidth(0, 16 * 256);
        sheet.setColumnWidth(1, 22 * 256);
        sheet.setColumnWidth(2, 78 * 256);
    }

    private org.apache.poi.ss.usermodel.CellStyle createTemplateStyle(Workbook workbook,
                                                                       IndexedColors fill,
                                                                       IndexedColors fontColor,
                                                                       boolean bold,
                                                                       HorizontalAlignment alignment) {
        org.apache.poi.ss.usermodel.CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(alignment);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
        style.setBottomBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setTopBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setLeftBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setRightBorderColor(IndexedColors.GREY_25_PERCENT.getIndex());
        Font font = workbook.createFont();
        font.setBold(bold);
        font.setColor(fontColor.getIndex());
        style.setFont(font);
        return style;
    }

    private void setCell(Row row, int index, String value, org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(index);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private String targetTableForField(String field) {
        if (List.of(
                "目标类别",
                "目标物质类别",
                "目标物质子类",
                "目标物质细类",
                "药物",
                "适应症",
                "处方/非处方",
                "生物标记物名称",
                "biomarker",
                "生物标记物CAS",
                "理化性质",
                "校准系数",
                "人体排泄率（%）",
                "药物消费量_value",
                "药物消费量_unit",
                "药物使用流行率（%）",
                "疾病患病率（%）",
                "DOI",
                "keywords",
                "abstract"
        ).contains(field)) {
            return "compounds";
        }
        if (List.of(
                "采样方法",
                "分析方法",
                "MDL_value",
                "MDL_unit",
                "MQL_value",
                "MQL_unit",
                "IDL_value",
                "IDL_unit",
                "IQL_value",
                "IQL_unit"
        ).contains(field)) {
            return "analytical_methods";
        }
        if (List.of(
                "污水厂名称",
                "污水厂处理规模（m3/day）",
                "汇水区人群数量",
                "污水厂位置_国",
                "污水厂位置_省",
                "污水厂位置_市",
                "GS管道衰减系数"
        ).contains(field)) {
            return "wastewater_plants";
        }
        if (List.of("样品采集时间", "采样开始时间_YYYY_MM", "采样结束时间_YYYY_MM").contains(field)) {
            return "sampling_events";
        }
        if (List.of(
                "做图浓度_value",
                "做图浓度_unit",
                "进水浓度min_value",
                "进水浓度min_unit",
                "进水浓度max_value",
                "进水浓度max_unit",
                "进水浓度average_value",
                "进水浓度average_unit",
                "进水浓度median_value",
                "进水浓度median_unit",
                "每日质量负荷DLs",
                "DLs_unit",
                "PNDL_value",
                "PNDL_unit",
                "PNDL估算_value",
                "PNDL估算_unit",
                "做图PNDL_value",
                "做图PNDL_unit"
        ).contains(field)) {
            return "measurements";
        }
        return "上传审计";
    }

    private String fieldGuideForField(String field) {
        if (field.endsWith("_value") || field.endsWith("（%）") || field.equals("每日质量负荷DLs")
                || field.equals("GS管道衰减系数") || field.equals("校准系数")
                || field.equals("污水厂处理规模（m3/day）") || field.equals("汇水区人群数量")) {
            return "建议填写数值；NA、ND、<LOD、区间值会保留原始值并生成警告。";
        }
        if (field.endsWith("_unit")) {
            return "填写与 value 对应的单位，可为 NA。";
        }
        if (List.of("文献编号", "目标类别", "目标物质类别", "采样方法", "分析方法").contains(field)) {
            return "关键字段，不能为空或 NA。";
        }
        if (List.of("药物", "生物标记物名称", "biomarker").contains(field)) {
            return "三者至少填写一个，用于形成目标物记录。";
        }
        return "按 WBE 汇总表原字段填写；无数据时可填写 NA。";
    }

    private String targetTableForOptionalField(String field) {
        if (List.of("采样点编号", "污水厂详细地址", "污水厂纬度", "污水厂经度").contains(field)) {
            return "reported_sites";
        }
        if (List.of("confirmed_site_id", "点位确认依据").contains(field)) {
            return "confirmed_sites / 确认审计";
        }
        return "sampling_events / 上传审计";
    }

    private String optionalFieldGuide(String field) {
        return switch (field) {
            case "采样点编号" -> "文献内点位标识，优先于污水厂名称参与 reported_site_key 计算。";
            case "污水厂详细地址" -> "用于人工核查真实污水厂，不会自动触发跨文献合并。";
            case "污水厂纬度" -> "十进制度数，范围 -90 至 90，仅作为人工核查证据。";
            case "污水厂经度" -> "十进制度数，范围 -180 至 180，仅作为人工核查证据。";
            case "confirmed_site_id" -> "人工确认的真实污水厂 ID；填写后必须同时填写点位确认依据。";
            case "点位确认依据" -> "说明同名、地址、坐标或同一项目等明确证据，随审核记录持久化。";
            default -> "用于重复检查和来源追踪；没有可留空。";
        };
    }

    private Long getOrCreateCompound(Map<String, String> data) {
        String targetCategory = valueOrFallback(data.get("目标类别"), "NA");
        String substanceCategory = valueOrFallback(data.get("目标物质类别"), "NA");
        String substanceSubclass = valueOrNull(data.get("目标物质子类"));
        String substanceFine = valueOrNull(data.get("目标物质细类"));
        String biomarkerName = valueOrNull(data.get("生物标记物名称"));
        String biomarkerCas = valueOrNull(data.get("生物标记物CAS"));
        String drugName = firstUseful(data.get("药物"), data.get("生物标记物名称"), data.get("biomarker"), "NA");
        Long existing = queryOptionalLong("""
                        SELECT compound_id
                        FROM compounds
                        WHERE drug_name = ?
                          AND target_category = ?
                          AND substance_category = ?
                          AND ((substance_subclass IS NULL AND ? IS NULL) OR substance_subclass = ?)
                          AND ((substance_fine IS NULL AND ? IS NULL) OR substance_fine = ?)
                          AND ((biomarker_name IS NULL AND ? IS NULL) OR biomarker_name = ?)
                          AND ((biomarker_cas IS NULL AND ? IS NULL) OR biomarker_cas = ?)
                        LIMIT 1
                        """,
                drugName,
                targetCategory,
                substanceCategory,
                substanceSubclass,
                substanceSubclass,
                substanceFine,
                substanceFine,
                biomarkerName,
                biomarkerName,
                biomarkerCas,
                biomarkerCas
        );
        if (existing != null) {
            return existing;
        }
        return insertAndReturnKey("""
                        INSERT INTO compounds (
                            target_category,
                            substance_category,
                            substance_subclass,
                            substance_fine,
                            drug_name,
                            indications,
                            prescription_type,
                            biomarker_name,
                            biomarker_cas,
                            physicochemical_properties,
                            calibration_coefficient,
                            human_excretion_rate,
                            consumption_value,
                            consumption_unit,
                            usage_prevalence,
                            disease_prevalence,
                            keywords,
                            doi,
                            abstract
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                targetCategory,
                substanceCategory,
                substanceSubclass,
                substanceFine,
                drugName,
                valueOrNull(data.get("适应症")),
                safePrescriptionType(data.get("处方/非处方")),
                biomarkerName,
                biomarkerCas,
                valueOrNull(data.get("理化性质")),
                parseDecimalForDatabase(data.get("校准系数")),
                parseDecimalForDatabase(data.get("人体排泄率（%）")),
                parseDecimalForDatabase(data.get("药物消费量_value")),
                valueOrNull(data.get("药物消费量_unit")),
                parseDecimalForDatabase(data.get("药物使用流行率（%）")),
                parseDecimalForDatabase(data.get("疾病患病率（%）")),
                valueOrNull(data.get("keywords")),
                valueOrNull(data.get("DOI")),
                valueOrNull(data.get("abstract"))
        );
    }

    private Long getOrCreateMethod(Map<String, String> data) {
        String samplingMethod = valueOrFallback(data.get("采样方法"), "NA");
        String analysisMethod = valueOrFallback(data.get("分析方法"), "NA");
        BigDecimal mdlValue = parseDecimalForDatabase(data.get("MDL_value"));
        String mdlUnit = valueOrNull(data.get("MDL_unit"));
        BigDecimal mqlValue = parseDecimalForDatabase(data.get("MQL_value"));
        String mqlUnit = valueOrNull(data.get("MQL_unit"));
        BigDecimal idlValue = parseDecimalForDatabase(data.get("IDL_value"));
        String idlUnit = valueOrNull(data.get("IDL_unit"));
        BigDecimal iqlValue = parseDecimalForDatabase(data.get("IQL_value"));
        String iqlUnit = valueOrNull(data.get("IQL_unit"));
        Long existing = queryOptionalLong("""
                        SELECT method_id
                        FROM analytical_methods
                        WHERE sampling_method = ?
                          AND analysis_method = ?
                          AND ((mdl_value IS NULL AND ? IS NULL) OR mdl_value = ?)
                          AND ((mdl_unit IS NULL AND ? IS NULL) OR mdl_unit = ?)
                          AND ((mql_value IS NULL AND ? IS NULL) OR mql_value = ?)
                          AND ((mql_unit IS NULL AND ? IS NULL) OR mql_unit = ?)
                          AND ((idl_value IS NULL AND ? IS NULL) OR idl_value = ?)
                          AND ((idl_unit IS NULL AND ? IS NULL) OR idl_unit = ?)
                          AND ((iql_value IS NULL AND ? IS NULL) OR iql_value = ?)
                          AND ((iql_unit IS NULL AND ? IS NULL) OR iql_unit = ?)
                        LIMIT 1
                        """,
                samplingMethod,
                analysisMethod,
                mdlValue,
                mdlValue,
                mdlUnit,
                mdlUnit,
                mqlValue,
                mqlValue,
                mqlUnit,
                mqlUnit,
                idlValue,
                idlValue,
                idlUnit,
                idlUnit,
                iqlValue,
                iqlValue,
                iqlUnit,
                iqlUnit
        );
        if (existing != null) {
            return existing;
        }
        return insertAndReturnKey("""
                        INSERT INTO analytical_methods (
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                samplingMethod,
                analysisMethod,
                mdlValue,
                mdlUnit,
                mqlValue,
                mqlUnit,
                idlValue,
                idlUnit,
                iqlValue,
                iqlUnit
        );
    }

    private Long getOrCreatePlant(Map<String, String> data) {
        String plantName = valueOrFallback(data.get("污水厂名称"), "NA");
        String country = valueOrFallback(data.get("污水厂位置_国"), "NA");
        String province = valueOrFallback(data.get("污水厂位置_省"), "NA");
        String city = valueOrFallback(data.get("污水厂位置_市"), "NA");
        Long existing = queryOptionalLong("""
                        SELECT plant_id
                        FROM wastewater_plants
                        WHERE plant_name = ?
                          AND country = ?
                          AND province = ?
                          AND city = ?
                        LIMIT 1
                        """,
                plantName,
                country,
                province,
                city
        );
        if (existing != null) {
            return existing;
        }
        return insertAndReturnKey("""
                        INSERT INTO wastewater_plants (
                            plant_name,
                            treatment_capacity_m3_day,
                            served_population,
                            country,
                            province,
                            city,
                            gs_attenuation_coefficient
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                plantName,
                parseLongForDatabase(data.get("污水厂处理规模（m3/day）")),
                parseLongForDatabase(data.get("汇水区人群数量")),
                country,
                province,
                city,
                parseDecimalForDatabase(data.get("GS管道衰减系数"))
        );
    }

    private String upsertReportedSite(Map<String, String> data, Long uploadId, Long reviewedBy) {
        ReportedSiteIdentity.Identity identity = reportedSiteIdentity(data);
        String reportedSiteKey = identity.reportedSiteKey();
        String confirmedSiteId = valueOrNull(data.get("confirmed_site_id"));
        String evidence = valueOrNull(data.get("点位确认依据"));
        String country = valueOrNull(data.get("污水厂位置_国"));
        String existingAssignment = queryOptionalString(
                "SELECT confirmed_site_id FROM reported_sites WHERE reported_site_key = ?", reportedSiteKey);

        if (confirmedSiteId != null) {
            if (evidence == null) {
                throw new BusinessException("confirmed_site_id 必须提供点位确认依据");
            }
            if (country == null) {
                throw new BusinessException("人工确认真实点位时必须填写国家");
            }
            String existingCountry = queryOptionalString(
                    "SELECT country FROM confirmed_sites WHERE confirmed_site_id = ?", confirmedSiteId);
            if (existingCountry != null
                    && !ReportedSiteIdentity.normalize(existingCountry).equals(ReportedSiteIdentity.normalize(country))) {
                throw new BusinessException("confirmed_site_id “" + confirmedSiteId + "”不能跨国家复用");
            }
            if (existingAssignment != null && !existingAssignment.equals(confirmedSiteId)) {
                throw new BusinessException("文献内点位已绑定其他 confirmed_site_id，不能直接改绑");
            }
            jdbcTemplate.update("""
                            INSERT INTO confirmed_sites (
                                confirmed_site_id, canonical_name, country, province, city,
                                detailed_address, latitude, longitude, confirmation_evidence,
                                confirmed_by, confirmed_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())
                            ON DUPLICATE KEY UPDATE
                                canonical_name = COALESCE(NULLIF(VALUES(canonical_name), ''), canonical_name),
                                province = COALESCE(NULLIF(VALUES(province), ''), province),
                                city = COALESCE(NULLIF(VALUES(city), ''), city),
                                detailed_address = COALESCE(NULLIF(VALUES(detailed_address), ''), detailed_address),
                                latitude = COALESCE(VALUES(latitude), latitude),
                                longitude = COALESCE(VALUES(longitude), longitude),
                                confirmation_evidence = VALUES(confirmation_evidence),
                                confirmed_by = VALUES(confirmed_by),
                                confirmed_at = VALUES(confirmed_at)
                            """,
                    confirmedSiteId,
                    valueOrNull(data.get("污水厂名称")),
                    country,
                    valueOrNull(data.get("污水厂位置_省")),
                    valueOrNull(data.get("污水厂位置_市")),
                    valueOrNull(data.get("污水厂详细地址")),
                    parseDecimalForDatabase(data.get("污水厂纬度")),
                    parseDecimalForDatabase(data.get("污水厂经度")),
                    evidence,
                    reviewedBy);
        }

        jdbcTemplate.update("""
                        INSERT INTO reported_sites (
                            reported_site_key, literature_code, raw_plant_name, sampling_site_code,
                            country, province, city, detailed_address, latitude, longitude,
                            key_quality, confirmed_site_id, confirmation_evidence, confirmed_by, confirmed_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CASE WHEN ? IS NULL THEN NULL ELSE NOW() END)
                        ON DUPLICATE KEY UPDATE
                            raw_plant_name = VALUES(raw_plant_name),
                            sampling_site_code = VALUES(sampling_site_code),
                            country = VALUES(country),
                            province = VALUES(province),
                            city = VALUES(city),
                            detailed_address = COALESCE(NULLIF(VALUES(detailed_address), ''), detailed_address),
                            latitude = COALESCE(VALUES(latitude), latitude),
                            longitude = COALESCE(VALUES(longitude), longitude),
                            key_quality = VALUES(key_quality),
                            confirmed_site_id = COALESCE(VALUES(confirmed_site_id), confirmed_site_id),
                            confirmation_evidence = COALESCE(VALUES(confirmation_evidence), confirmation_evidence),
                            confirmed_by = COALESCE(VALUES(confirmed_by), confirmed_by),
                            confirmed_at = CASE WHEN VALUES(confirmed_site_id) IS NULL THEN confirmed_at ELSE VALUES(confirmed_at) END
                        """,
                reportedSiteKey,
                valueOrFallback(data.get("文献编号"), "__missing_literature__"),
                valueOrNull(data.get("污水厂名称")),
                valueOrNull(data.get("采样点编号")),
                country,
                valueOrNull(data.get("污水厂位置_省")),
                valueOrNull(data.get("污水厂位置_市")),
                valueOrNull(data.get("污水厂详细地址")),
                parseDecimalForDatabase(data.get("污水厂纬度")),
                parseDecimalForDatabase(data.get("污水厂经度")),
                identity.keyQuality(),
                confirmedSiteId,
                evidence,
                confirmedSiteId == null ? null : reviewedBy,
                confirmedSiteId);

        if (confirmedSiteId != null && existingAssignment == null) {
            jdbcTemplate.update("""
                            INSERT INTO reported_site_confirmation_audit (
                                reported_site_key, previous_confirmed_site_id, confirmed_site_id,
                                confirmation_evidence, reviewed_by, reviewed_at, upload_id, action
                            ) VALUES (?, NULL, ?, ?, ?, NOW(), ?, 'CONFIRM')
                            """,
                    reportedSiteKey, confirmedSiteId, evidence, reviewedBy, uploadId);
        }
        return reportedSiteKey;
    }

    private ReportedSiteIdentity.Identity reportedSiteIdentity(Map<String, String> data) {
        return ReportedSiteIdentity.create(
                data.get("文献编号"),
                data.get("污水厂位置_国"),
                data.get("污水厂位置_省"),
                data.get("污水厂位置_市"),
                data.get("采样点编号"),
                data.get("污水厂名称"));
    }

    private Long insertSamplingEvent(Map<String, String> data, Long plantId, String reportedSiteKey) {
        return insertAndReturnKey("""
                        INSERT INTO sampling_events (
                            plant_id,
                            reported_site_key,
                            sample_collection_time,
                            sampling_start_ym,
                            sampling_end_ym,
                            source_workbook,
                            original_row_number
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                plantId,
                reportedSiteKey,
                parseDateTimeForDatabase(data.get("样品采集时间")),
                valueOrNull(data.get("采样开始时间_YYYY_MM")),
                valueOrNull(data.get("采样结束时间_YYYY_MM")),
                valueOrNull(data.get("来源工作簿说明")),
                parseIntegerForDatabase(data.get("原表行号说明"))
        );
    }

    private Long insertMeasurement(Map<String, String> data,
                                   Long compoundId,
                                   Long methodId,
                                   Long eventId,
                                   Long uploadId,
                                   Long rowId,
                                   String dedupeKey) {
        return insertAndReturnKey("""
                        INSERT INTO measurements (
                            compound_id,
                            method_id,
                            event_id,
                            upload_id,
                            upload_row_id,
                            literature_code,
                            raw_payload,
                            dedupe_key,
                            plot_concentration_value,
                            plot_concentration_unit,
                            inflow_min_value,
                            inflow_min_unit,
                            inflow_max_value,
                            inflow_max_unit,
                            inflow_avg_value,
                            inflow_avg_unit,
                            inflow_median_value,
                            inflow_median_unit,
                            daily_load_dls_value,
                            daily_load_dls_unit,
                            pndl_value,
                            pndl_unit,
                            pndl_estimated_value,
                            pndl_estimated_unit,
                            plot_pndl_value,
                            plot_pndl_unit
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                compoundId,
                methodId,
                eventId,
                uploadId,
                rowId,
                valueOrNull(data.get("文献编号")),
                toJson(data),
                dedupeKey,
                parseDecimalForDatabase(data.get("做图浓度_value")),
                valueOrNull(data.get("做图浓度_unit")),
                parseDecimalForDatabase(data.get("进水浓度min_value")),
                valueOrNull(data.get("进水浓度min_unit")),
                parseDecimalForDatabase(data.get("进水浓度max_value")),
                valueOrNull(data.get("进水浓度max_unit")),
                parseDecimalForDatabase(data.get("进水浓度average_value")),
                valueOrNull(data.get("进水浓度average_unit")),
                parseDecimalForDatabase(data.get("进水浓度median_value")),
                valueOrNull(data.get("进水浓度median_unit")),
                parseDecimalForDatabase(data.get("每日质量负荷DLs")),
                valueOrNull(data.get("DLs_unit")),
                parseDecimalForDatabase(data.get("PNDL_value")),
                valueOrNull(data.get("PNDL_unit")),
                parseDecimalForDatabase(data.get("PNDL估算_value")),
                valueOrNull(data.get("PNDL估算_unit")),
                parseDecimalForDatabase(data.get("做图PNDL_value")),
                valueOrNull(data.get("做图PNDL_unit"))
        );
    }

    private ParsedWorkbook parseWorkbook(Path workbookPath) {
        IOUtils.setByteArrayMaxOverride(256 * 1024 * 1024);
        try (Workbook workbook = WorkbookFactory.create(workbookPath.toFile(), null, true)) {
            if (!(workbook instanceof XSSFWorkbook)) {
                throw new BusinessException("文件内容不是有效的 .xlsx 工作簿");
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<String> headerErrors = new ArrayList<>();
            for (String sheetName : REQUIRED_SHEETS) {
                if (workbook.getSheet(sheetName) == null) {
                    headerErrors.add("上传文件必须包含名为“" + sheetName + "”的工作表");
                }
            }
            if (!headerErrors.isEmpty()) return new ParsedWorkbook(headerErrors, List.of());

            List<ParsedRow> rows = new ArrayList<>();
            Sheet dataSheet = workbook.getSheet(DATA_SHEET_NAME);
            List<String> dataRequiredHeaders = hasHeaderAt(dataSheet, 4, "目标物质细类", formatter)
                    ? REQUIRED_HEADERS : LEGACY_REQUIRED_HEADERS;
            rows.addAll(parseSheet(dataSheet, DATA_SHEET_NAME,
                    combinedDataHeaders(dataRequiredHeaders), dataRequiredHeaders, formatter, headerErrors));
            rows.addAll(parseSheet(workbook.getSheet(SITE_SHEET_NAME), SITE_SHEET_NAME,
                    SITE_HEADERS, SITE_HEADERS, formatter, headerErrors));
            Sheet icd11Sheet = workbook.getSheet(ICD11_SHEET_NAME);
            List<String> icd11Headers = hasHeaderAt(icd11Sheet, 3, "目标物质细类", formatter)
                    ? ICD11_HEADERS : LEGACY_ICD11_HEADERS;
            rows.addAll(parseSheet(icd11Sheet, ICD11_SHEET_NAME,
                    icd11Headers, icd11Headers, formatter, headerErrors));
            rows.addAll(parseSheet(workbook.getSheet(LITERATURE_SHEET_NAME), LITERATURE_SHEET_NAME,
                    LITERATURE_HEADERS, LITERATURE_HEADERS, formatter, headerErrors));
            if (headerErrors.isEmpty()) addCrossSheetValidation(rows);
            return new ParsedWorkbook(headerErrors, rows);
        } catch (Exception ex) {
            throw new BusinessException("Excel 文件无法读取或格式损坏：" + safeExceptionMessage(ex));
        }
    }

    private List<ParsedRow> parseSheet(Sheet sheet,
                                       String sheetName,
                                       List<String> readHeaders,
                                       List<String> requiredHeaders,
                                       DataFormatter formatter,
                                       List<String> headerErrors) {
        List<String> errors = validateHeaders(sheet, sheetName, requiredHeaders, formatter);
        headerErrors.addAll(errors);
        if (!errors.isEmpty()) return List.of();
        List<ParsedRow> rows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();
        for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
            Map<String, String> data = readRow(sheet.getRow(rowIndex), readHeaders, formatter);
            if (isBlankRow(data, readHeaders)) continue;
            rows.add(validateRow(sheetName, rowIndex + 1, data));
        }
        return rows;
    }

    private List<String> validateHeaders(Sheet sheet,
                                         String sheetName,
                                         List<String> expectedHeaders,
                                         DataFormatter formatter) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) return List.of(sheetName + " 第一行必须是表头");
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < expectedHeaders.size(); i++) {
            String actual = normalizeCell(formatter.formatCellValue(headerRow.getCell(i)));
            String expected = expectedHeaders.get(i);
            if (!expected.equals(actual)) {
                errors.add(sheetName + " 第 " + (i + 1) + " 列应为“" + expected
                        + "”，实际为“" + blankForDisplay(actual) + "”");
            }
        }
        return errors;
    }

    private boolean hasHeaderAt(Sheet sheet, int columnIndex, String expected, DataFormatter formatter) {
        Row header = sheet == null ? null : sheet.getRow(0);
        return header != null && expected.equals(normalizeCell(formatter.formatCellValue(header.getCell(columnIndex))));
    }

    private Map<String, String> readRow(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> data = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            String value = row == null ? "" : formatter.formatCellValue(row.getCell(i));
            data.put(headers.get(i), value == null ? "" : value);
        }
        return data;
    }

    private ParsedRow validateRow(String sheetName, int excelRowNumber, Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (DATA_SHEET_NAME.equals(sheetName)) {
            validateDataRow(data, errors, warnings);
        } else if (SITE_SHEET_NAME.equals(sheetName)) {
            validateSiteRow(data, errors, warnings);
        } else if (ICD11_SHEET_NAME.equals(sheetName)) {
            validateIcd11Row(data, errors, warnings);
        } else {
            requireUseful(data, "文献编号", errors);
            if (!isUseful(data.get("DOI"))) warnings.add("DOI 为空，仍保留文献记录并计入 DOI 缺失统计");
        }
        return new ParsedRow(sheetName, excelRowNumber, data, errors, warnings);
    }

    private void validateDataRow(Map<String, String> data, List<String> errors, List<String> warnings) {
        requireUseful(data, "文献编号", errors);
        requireUseful(data, "目标类别", errors);
        requireUseful(data, "目标物质类别", errors);
        requireUseful(data, "采样方法", errors);
        requireUseful(data, "分析方法", errors);
        if (!isUseful(data.get("药物")) && !isUseful(data.get("生物标记物名称")) && !isUseful(data.get("biomarker"))) {
            errors.add("药物、生物标记物名称、biomarker 至少需要填写一个");
        } else if (!isUseful(data.get("药物"))) {
            warnings.add("药物为空，入库时将使用生物标记物名称或 biomarker 作为药物名称兜底");
        }
        if (!isUseful(data.get("污水厂名称"))) {
            if (!isUseful(data.get("采样点编号"))) {
                warnings.add("污水厂名称和采样点编号均为空，将按同一文献内同一国家、省州、市合并为一个低置信度点位");
            } else {
                warnings.add("污水厂名称为空，将使用采样点编号标识文献内点位");
            }
        }
        String confirmedSiteId = valueOrNull(data.get("confirmed_site_id"));
        if (confirmedSiteId != null && !isUseful(data.get("点位确认依据"))) {
            errors.add("填写 confirmed_site_id 时必须填写点位确认依据，并经过上传审核");
        }
        if (confirmedSiteId != null && !isUseful(data.get("污水厂位置_国"))) {
            errors.add("填写 confirmed_site_id 时必须填写污水厂位置_国");
        }
        validateCoordinate(data.get("污水厂纬度"), "污水厂纬度", new BigDecimal("-90"), new BigDecimal("90"), errors);
        validateCoordinate(data.get("污水厂经度"), "污水厂经度", new BigDecimal("-180"), new BigDecimal("180"), errors);
        if (isUseful(data.get("样品采集时间")) && parseSampleCollectionTime(data.get("样品采集时间"), warnings) == null) {
            // parseSampleCollectionTime already records a field-level warning.
        }
        if (!isUseful(data.get("来源工作簿说明")) || parseIntegerForDatabase(data.get("原表行号说明")) == null) {
            warnings.add("来源工作簿说明或原表行号说明缺失，系统将使用文献编号、采样信息和测量值生成重复判断键");
        }
        validateDecimalFields(data, warnings);
    }

    private void validateSiteRow(Map<String, String> data, List<String> errors, List<String> warnings) {
        requireUseful(data, "文献编号", errors);
        String includeValue = normalizeCell(data.get("是否计入点位数"));
        if (!Set.of("是", "否", "yes", "no", "y", "n", "true", "false", "1", "0")
                .contains(includeValue.toLowerCase(Locale.ROOT))) {
            errors.add("是否计入点位数只能填写是/否");
        }
        boolean included = SiteLinkageMatcher.included(includeValue);
        String siteKey = SiteLinkageMatcher.text(data.get("reported_site_key"));
        if (included && siteKey.isEmpty()) {
            errors.add("计入点位数时 reported_site_key 不能为空");
        }
        if (siteKey.length() > 64) {
            errors.add("reported_site_key 不能超过 64 个字符");
        }
        if (!included && !isUseful(data.get("点位说明"))) {
            warnings.add("不计入点位数时建议填写点位说明或排除原因");
        }
        if (isUseful(data.get("confirmed_site_id")) && !isUseful(data.get("同一污水厂确认依据"))) {
            warnings.add("confirmed_site_id 当前仅作为候选；建议填写同一污水厂确认依据");
        }
        if (included && !isUseful(data.get("国家"))) {
            warnings.add("计入点位数但国家为空，该点位会保留在 QC 未映射清单中，不进入地图区域统计");
        }
    }

    private void validateCoordinate(String rawValue,
                                    String field,
                                    BigDecimal minimum,
                                    BigDecimal maximum,
                                    List<String> errors) {
        if (!isUseful(rawValue)) return;
        BigDecimal value = parseDecimalForDatabase(rawValue);
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            errors.add(field + "必须是 " + minimum.toPlainString() + " 至 " + maximum.toPlainString() + " 之间的十进制度数");
        }
    }

    private void validateIcd11Row(Map<String, String> data, List<String> errors, List<String> warnings) {
        for (String field : List.of("目标类别", "目标物质类别", "药物", "生物标记物名称",
                "ICD11_Level1_Name", "ICD11_Level2_Name", "映射层级", "是否进入桑基图")) {
            requireUseful(data, field, errors);
        }
        String mappingLevel = normalizeCell(data.get("映射层级"));
        boolean hasLevel3Code = isUseful(data.get("ICD11_Level3_Code"));
        boolean hasLevel3Name = isUseful(data.get("ICD11_Level3_Name"));
        if (hasLevel3Code != hasLevel3Name) errors.add("ICD11 Level3 编码和名称必须同时填写或同时留空");
        if ("Level2".equals(mappingLevel) && (hasLevel3Code || hasLevel3Name)) {
            errors.add("映射层级为 Level2 时不能填写 Level3");
        } else if ("Level3".equals(mappingLevel) && (!hasLevel3Code || !hasLevel3Name)) {
            errors.add("映射层级为 Level3 时必须填写完整 Level3");
        } else if (!Set.of("Level2", "Level3").contains(mappingLevel)) {
            errors.add("映射层级只能是 Level2 或 Level3");
        }
        String inSankey = normalizeCell(data.get("是否进入桑基图"));
        if (!Set.of("是", "否").contains(inSankey)) errors.add("是否进入桑基图只能填写“是”或“否”");
        if ("否".equals(inSankey) && !isUseful(data.get("不入图原因"))) {
            errors.add("不进入桑基图时必须填写不入图原因");
        }
        if (!isUseful(data.get("复核状态"))) warnings.add("复核状态为空，建议复核后再发布");
    }

    private void addCrossSheetValidation(List<ParsedRow> rows) {
        Set<String> literatureCodes = new LinkedHashSet<>();
        Map<String, String> literatureDois = new LinkedHashMap<>();
        rows.stream()
                .filter(row -> LITERATURE_SHEET_NAME.equals(row.sheetName()))
                .forEach(row -> {
                    String code = valueOrNull(row.data().get("文献编号"));
                    if (code == null) return;
                    if (!literatureCodes.add(code)) row.errors().add("文献编号在文献基础信息中重复：" + code);
                    String doi = valueOrNull(row.data().get("DOI"));
                    if (doi != null) literatureDois.put(code, normalizeDoi(doi));
                });

        rows.stream()
                .filter(row -> DATA_SHEET_NAME.equals(row.sheetName()))
                .forEach(row -> {
                    String code = valueOrNull(row.data().get("文献编号"));
                    if (code != null && !literatureCodes.contains(code)) {
                        row.errors().add("文献编号“" + code + "”未在文献基础信息中定义");
                    }
                });

        Set<String> reportedSiteKeys = new LinkedHashSet<>();
        rows.stream()
                .filter(row -> SITE_SHEET_NAME.equals(row.sheetName()))
                .forEach(row -> {
                    String code = valueOrNull(row.data().get("文献编号"));
                    if (code != null && !literatureCodes.contains(code)) {
                        row.errors().add("文献编号“" + code + "”未在文献基础信息中定义");
                    }
                    String siteKey = SiteLinkageMatcher.text(row.data().get("reported_site_key"));
                    if (!siteKey.isEmpty() && !reportedSiteKeys.add(siteKey)) {
                        row.errors().add("reported_site_key 在点位关联表中重复：" + siteKey);
                    }
                });

        validateReportedSiteConfirmations(rows);

        rows.stream()
                .filter(row -> ICD11_SHEET_NAME.equals(row.sheetName()))
                .forEach(row -> validateIcd11Sources(row, literatureCodes, literatureDois));
    }

    private void validateIcd11Sources(ParsedRow row,
                                      Set<String> literatureCodes,
                                      Map<String, String> literatureDois) {
        Map<String, String> data = row.data();
        List<String> sourceCodes = splitSourceList(data.get("涉及文献编号"));
        List<String> sourceDois = splitSourceList(data.get("涉及DOI"));
        for (String code : sourceCodes) {
            if (!literatureCodes.contains(code)) {
                row.errors().add("涉及文献编号“" + code + "”未在文献基础信息中定义");
            }
        }
        Integer declaredLiteratureCount = parseIntegerForDatabase(data.get("涉及文献数"));
        if (declaredLiteratureCount != null && declaredLiteratureCount != sourceCodes.size()) {
            row.warnings().add("涉及文献数为 " + declaredLiteratureCount
                    + "，但涉及文献编号列表包含 " + sourceCodes.size() + " 项");
        }
        long uniqueDoiCount = sourceDois.stream().map(this::normalizeDoi).distinct().count();
        Integer declaredUniqueDoiCount = parseIntegerForDatabase(data.get("唯一DOI数"));
        if (declaredUniqueDoiCount != null && declaredUniqueDoiCount != uniqueDoiCount) {
            row.warnings().add("唯一DOI数为 " + declaredUniqueDoiCount
                    + "，但涉及DOI列表去重后为 " + uniqueDoiCount + " 项");
        }
        long missingDoiCount = sourceCodes.stream()
                .filter(code -> !literatureDois.containsKey(code))
                .count();
        Integer declaredMissingDoiCount = parseIntegerForDatabase(data.get("DOI缺失数"));
        if (declaredMissingDoiCount != null && declaredMissingDoiCount != missingDoiCount) {
            row.warnings().add("DOI缺失数为 " + declaredMissingDoiCount
                    + "，按文献基础信息计算为 " + missingDoiCount + " 项");
        }
        Set<String> listedDois = new LinkedHashSet<>();
        sourceDois.forEach(doi -> listedDois.add(normalizeDoi(doi)));
        for (String code : sourceCodes) {
            String literatureDoi = literatureDois.get(code);
            if (literatureDoi != null && !listedDois.contains(literatureDoi)) {
                row.warnings().add("文献“" + code + "”的 DOI 未出现在涉及DOI列表中");
            }
        }
    }

    private List<String> combinedDataHeaders(List<String> requiredHeaders) {
        List<String> headers = new ArrayList<>(requiredHeaders);
        headers.addAll(OPTIONAL_HEADERS);
        return headers;
    }

    private void validateReportedSiteConfirmations(List<ParsedRow> rows) {
        Map<String, String> countryByConfirmedId = new LinkedHashMap<>();
        Map<String, String> existingConfirmedCountries = new LinkedHashMap<>();
        for (ParsedRow row : rows) {
            if (!SITE_SHEET_NAME.equals(row.sheetName())) continue;
            Map<String, String> data = row.data();
            String confirmedSiteId = valueOrNull(data.get("confirmed_site_id"));
            if (confirmedSiteId == null) continue;
            String country = SiteLinkageMatcher.normalized(data.get("国家"));

            String priorCountry = countryByConfirmedId.putIfAbsent(confirmedSiteId, country);
            if (priorCountry != null && !priorCountry.equals(country)) {
                row.errors().add("confirmed_site_id “" + confirmedSiteId + "”不能跨国家使用");
            }

            if (tableExists("confirmed_sites")) {
                String existingCountry = existingConfirmedCountries.computeIfAbsent(confirmedSiteId, id -> queryOptionalString(
                        "SELECT country FROM confirmed_sites WHERE confirmed_site_id = ?", id));
                if (existingCountry != null
                        && !SiteLinkageMatcher.normalized(existingCountry).equals(country)) {
                    row.errors().add("confirmed_site_id “" + confirmedSiteId + "”已归属其他国家");
                }
            }
        }
    }

    private List<String> splitSourceList(String value) {
        String normalized = normalizeCell(value);
        if (normalized.isBlank() || isNaToken(normalized)) return List.of();
        return Pattern.compile("[;；、,，|\\n\\r]+")
                .splitAsStream(normalized)
                .map(String::trim)
                .filter(item -> !item.isBlank() && !isNaToken(item))
                .toList();
    }

    private String normalizeDoi(String value) {
        String normalized = normalizeCell(value).toLowerCase(Locale.ROOT);
        return normalized
                .replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "")
                .replaceFirst("^doi\\s*:\\s*", "")
                .trim();
    }

    private void validateDecimalFields(Map<String, String> data, List<String> warnings) {
        List<String> decimalFields = List.of(
                "污水厂处理规模（m3/day）",
                "汇水区人群数量",
                "校准系数",
                "MDL_value",
                "MQL_value",
                "IDL_value",
                "IQL_value",
                "做图浓度_value",
                "进水浓度min_value",
                "进水浓度max_value",
                "进水浓度average_value",
                "进水浓度median_value",
                "每日质量负荷DLs",
                "PNDL_value",
                "PNDL估算_value",
                "做图PNDL_value",
                "GS管道衰减系数",
                "人体排泄率（%）",
                "药物消费量_value",
                "药物使用流行率（%）",
                "疾病患病率（%）"
        );
        for (String field : decimalFields) {
            parseDecimal(data.get(field), field, warnings);
        }
    }

    private BigDecimal parseDecimal(String value, String field, List<String> warnings) {
        String normalized = normalizeCell(value);
        if (normalized.isBlank() || isNaToken(normalized)) {
            return null;
        }
        String compact = normalized.replace(",", "").replace("，", "");
        try {
            return new BigDecimal(compact);
        } catch (NumberFormatException ignored) {
            // Continue into supported WBE text forms below.
        }
        if (compact.startsWith("<") || compact.startsWith(">") || compact.equalsIgnoreCase("ND")
                || compact.equalsIgnoreCase("BQL")) {
            warnings.add(field + " 为“" + normalized + "”，属于低于检出/定量限或未检出表达，业务数值列将写入 NULL，原值保留在上传行 JSON");
            return null;
        }
        if (compact.contains("~") || compact.contains("～") || looksLikeRange(compact)) {
            Matcher preferredMatcher = PREFERRED_NUMBER.matcher(compact);
            if (preferredMatcher.find()) {
                warnings.add(field + " 为区间/说明文本“" + normalized + "”，入库时采用括号中的“取" + preferredMatcher.group(1) + "”");
                return new BigDecimal(preferredMatcher.group(1));
            }
            warnings.add(field + " 为区间/说明文本“" + normalized + "”，当前业务数值列将写入 NULL，原值保留在上传行 JSON");
            return null;
        }
        Matcher leadingMatcher = LEADING_NUMBER.matcher(compact);
        if (leadingMatcher.find()) {
            warnings.add(field + " 含说明文本“" + normalized + "”，入库时采用开头数值 " + leadingMatcher.group(1));
            return new BigDecimal(leadingMatcher.group(1));
        }
        warnings.add(field + " 的值“" + normalized + "”不是可入库数值，业务数值列将写入 NULL，原值保留在上传行 JSON");
        return null;
    }

    private boolean looksLikeRange(String value) {
        return Pattern.compile("\\d\\s*-\\s*\\d").matcher(value).find();
    }

    private BigDecimal parseDecimalForDatabase(String value) {
        return parseDecimal(value, "", new ArrayList<>());
    }

    private Long parseLongForDatabase(String value) {
        BigDecimal decimal = parseDecimalForDatabase(value);
        if (decimal == null) {
            return null;
        }
        return decimal.setScale(0, RoundingMode.HALF_UP).longValue();
    }

    private Integer parseIntegerForDatabase(String value) {
        BigDecimal decimal = parseDecimalForDatabase(value);
        if (decimal == null) {
            return null;
        }
        return decimal.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private LocalDateTime parseDateTimeForDatabase(String value) {
        return parseSampleCollectionTime(value, null);
    }

    private LocalDateTime parseSampleCollectionTime(String value, List<String> warnings) {
        String normalized = normalizeCell(value);
        if (normalized.isBlank() || isNaToken(normalized)) {
            return null;
        }
        String compact = normalized.replace("年", "-")
                .replace("月", "-")
                .replace("日", "")
                .replace("T", " ")
                .trim();
        for (DateTimeFormatter formatter : DATE_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(compact, formatter);
            } catch (DateTimeParseException ignored) {
                // Try the next supported date-time shape.
            }
        }
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                return LocalDate.parse(compact, formatter).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try the next supported date shape.
            }
        }
        for (DateTimeFormatter formatter : YEAR_MONTH_FORMATTERS) {
            try {
                return YearMonth.parse(compact, formatter).atDay(1).atStartOfDay();
            } catch (DateTimeParseException ignored) {
                // Try the next supported year-month shape.
            }
        }
        if (warnings != null) {
            warnings.add("样品采集时间“" + normalized + "”无法解析为日期时间，业务时间字段将写入 NULL，原值保留在上传行 JSON");
        }
        return null;
    }

    private String safePrescriptionType(String value) {
        String normalized = valueOrNull(value);
        if (normalized == null || !PRESCRIPTION_TYPES.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private String rowStatus(List<String> errors, List<String> warnings) {
        if (!errors.isEmpty()) {
            return ROW_STATUS_ERROR;
        }
        if (!warnings.isEmpty()) {
            return ROW_STATUS_WARNING;
        }
        return ROW_STATUS_VALID;
    }

    private void requireUseful(Map<String, String> data, String field, List<String> errors) {
        if (!isUseful(data.get(field))) {
            errors.add(field + " 不能为空或 NA");
        }
    }

    private boolean isBlankRow(Map<String, String> data, List<String> headers) {
        return headers.stream().allMatch(header -> normalizeCell(data.get(header)).isBlank());
    }

    private String normalizeCell(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isUseful(String value) {
        String normalized = normalizeCell(value);
        return !normalized.isBlank() && !isNaToken(normalized);
    }

    private boolean isNaToken(String value) {
        return SiteLinkageMatcher.text(value).isEmpty();
    }

    private String valueOrNull(String value) {
        return isUseful(value) ? normalizeCell(value) : null;
    }

    private String valueOrFallback(String value, String fallback) {
        return isUseful(value) ? normalizeCell(value) : fallback;
    }

    private String firstUseful(String... values) {
        for (String value : values) {
            if (isUseful(value)) {
                return normalizeCell(value);
            }
        }
        return "NA";
    }

    private String blankForDisplay(String value) {
        return value == null || value.isBlank() ? "空" : value;
    }

    private String validateAndNormalizeOriginalFileName(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的 Excel 文件");
        }
        long maxFileSize = storageProperties.getMaxFileSize().toBytes();
        if (file.getSize() > maxFileSize) {
            throw new BusinessException("上传文件不能超过 " + formatMegabytes(maxFileSize) + "MB");
        }
        String originalName = Optional.ofNullable(file.getOriginalFilename()).orElse("").trim();
        originalName = originalName.replace('\\', '/');
        int lastSeparator = originalName.lastIndexOf('/');
        if (lastSeparator >= 0) {
            originalName = originalName.substring(lastSeparator + 1);
        }
        originalName = originalName.replaceAll("[\\p{Cntrl}]", "").trim();
        if (originalName.isBlank()) {
            throw new BusinessException("上传文件名不能为空");
        }
        if (originalName.length() > 255) {
            throw new BusinessException("上传文件名不能超过 255 个字符");
        }
        if (!originalName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 文件");
        }
        return originalName;
    }

    private StagedFile stageUpload(MultipartFile file) {
        Path uploadDir = prepareUploadDirectory();
        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(uploadDir, ".wbe-upload-", ".tmp").normalize();
            ensurePathWithinRoot(temporaryFile, uploadDir);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long maxFileSize = storageProperties.getMaxFileSize().toBytes();
            long written = 0;
            try (InputStream rawInput = file.getInputStream();
                 DigestInputStream input = new DigestInputStream(rawInput, digest);
                 OutputStream output = Files.newOutputStream(
                         temporaryFile,
                         StandardOpenOption.WRITE,
                         StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    written += read;
                    if (written > maxFileSize) {
                        throw new BusinessException("上传文件不能超过 " + formatMegabytes(maxFileSize) + "MB");
                    }
                    output.write(buffer, 0, read);
                }
            }
            if (written == 0) {
                throw new BusinessException("上传文件不能为空");
            }
            return new StagedFile(temporaryFile, HexFormat.of().formatHex(digest.digest()));
        } catch (BusinessException ex) {
            deletePathQuietly(temporaryFile);
            throw ex;
        } catch (NoSuchAlgorithmException ex) {
            deletePathQuietly(temporaryFile);
            throw new BusinessException("无法计算文件校验值");
        } catch (IOException | SecurityException ex) {
            deletePathQuietly(temporaryFile);
            throw new BusinessException("保存上传文件失败：上传目录不可写或文件读取失败");
        }
    }

    private Path prepareUploadDirectory() {
        Path uploadDir;
        try {
            uploadDir = storageProperties.normalizedUploadDir();
            Files.createDirectories(uploadDir);
        } catch (IOException | SecurityException | IllegalStateException ex) {
            throw new BusinessException("上传目录不可写，请联系系统管理员");
        }
        if (!Files.isDirectory(uploadDir, LinkOption.NOFOLLOW_LINKS) || !Files.isWritable(uploadDir)) {
            throw new BusinessException("上传目录不可写，请联系系统管理员");
        }
        return uploadDir;
    }

    private StoredFile promoteStagedFile(StagedFile stagedFile) {
        Path uploadDir = prepareUploadDirectory();
        Path finalPath = uploadDir.resolve(UUID.randomUUID() + ".xlsx").normalize();
        ensurePathWithinRoot(finalPath, uploadDir);
        try {
            try {
                Files.move(stagedFile.path(), finalPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(stagedFile.path(), finalPath);
            }
        } catch (IOException | SecurityException ex) {
            deleteStagedFile(stagedFile);
            deletePathQuietly(finalPath);
            throw new BusinessException("保存上传文件失败");
        }
        registerRollbackCleanup(finalPath);
        return new StoredFile(finalPath);
    }

    private void registerRollbackCleanup(Path newFile) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deletePathQuietly(newFile);
            throw new BusinessException("上传事务未正确启动，文件未保存");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    deletePathQuietly(newFile);
                }
            }
        });
    }

    private Path resolveStoredFilePath(String storedFilePath) {
        final Path candidate;
        try {
            Path rawPath = Path.of(storedFilePath);
            candidate = rawPath.isAbsolute()
                    ? rawPath.normalize()
                    : Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
                    .resolve(rawPath).normalize();
        } catch (RuntimeException ex) {
            throw new BusinessException("原始文件路径无效");
        }
        Path configuredRoot = storageProperties.normalizedUploadDir();
        Path legacyRoot = legacyUploadDirectory();
        if (!candidate.startsWith(configuredRoot) && !candidate.startsWith(legacyRoot)) {
            throw new BusinessException("原始文件路径无效");
        }
        return candidate;
    }

    private Path legacyUploadDirectory() {
        return Path.of(System.getProperty("user.dir"), "uploads", "data").toAbsolutePath().normalize();
    }

    private void ensureRealPathWithinManagedRoots(Path path) {
        try {
            Path realPath = path.toRealPath();
            for (Path root : List.of(storageProperties.normalizedUploadDir(), legacyUploadDirectory())) {
                if (Files.exists(root) && realPath.startsWith(root.toRealPath())) {
                    return;
                }
            }
        } catch (IOException | SecurityException ex) {
            throw new BusinessException("原始文件路径无效");
        }
        throw new BusinessException("原始文件路径无效");
    }

    private String safeDownloadFileName(String fileName) {
        String normalized = Optional.ofNullable(fileName).orElse("原始数据.xlsx")
                .replace('\\', '/')
                .replaceAll("[\\p{Cntrl}]", "")
                .trim();
        int lastSeparator = normalized.lastIndexOf('/');
        if (lastSeparator >= 0) {
            normalized = normalized.substring(lastSeparator + 1);
        }
        return normalized.isBlank() ? "原始数据.xlsx" : normalized;
    }

    private void ensurePathWithinRoot(Path path, Path root) {
        if (!path.toAbsolutePath().normalize().startsWith(root.toAbsolutePath().normalize())) {
            throw new BusinessException("上传文件路径无效");
        }
    }

    private void deleteStagedFile(StagedFile stagedFile) {
        if (stagedFile == null) {
            return;
        }
        deletePathQuietly(stagedFile.path());
    }

    private void deletePathQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 清理失败不覆盖原始业务异常；仅清理本次生成的 UUID/临时文件。
        }
    }

    private String safeExceptionMessage(Exception ex) {
        String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : message;
    }

    private String formatMegabytes(long bytes) {
        return Long.toString(bytes / (1024 * 1024));
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder builder = new StringBuilder();
            for (byte b : hash) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new BusinessException("无法计算文件校验值");
        }
    }

    private Optional<String> findDuplicateMessage(String sha256) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM data_upload_batches WHERE sha256 = ?",
                Integer.class,
                sha256
        );
        if (count != null && count > 0) {
            return Optional.of("检测到相同 SHA256 的历史上传文件，请确认是否重复导入");
        }
        return Optional.empty();
    }

    private Long insertBatch(String fileName,
                             String storedFilePath,
                             String sha256,
                             Long uploadedBy,
                             String status,
                             int totalRows,
                             int validRows,
                             int errorRows,
                             int warningRows,
                             String duplicateMessage) {
        return insertAndReturnKey("""
                        INSERT INTO data_upload_batches (
                            file_name,
                            stored_file_path,
                            sha256,
                            uploaded_by,
                            status,
                            total_rows,
                            valid_rows,
                            error_rows,
                            warning_rows,
                            duplicate_message
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                fileName,
                storedFilePath,
                sha256,
                uploadedBy,
                status,
                totalRows,
                validRows,
                errorRows,
                warningRows,
                duplicateMessage
        );
    }

    private Long insertUploadRow(Long uploadId, ParsedRow row) {
        return insertAndReturnKey("""
                        INSERT INTO data_upload_rows (
                            upload_id,
                            sheet_name,
                            excel_row_number,
                            row_status,
                            raw_json,
                            error_json,
                            warning_json
                        )
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """,
                uploadId,
                row.sheetName(),
                row.excelRowNumber(),
                row.status(),
                toJson(row.data()),
                toJson(row.errors()),
                toJson(row.warnings())
        );
    }

    private Long insertAndReturnKey(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                statement.setObject(i + 1, args[i]);
            }
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new BusinessException("数据库未返回新增记录 ID");
        }
        return key.longValue();
    }

    private Long queryOptionalLong(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, Long.class, args);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private String queryOptionalString(String sql, Object... args) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class, args);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM information_schema.tables
                        WHERE table_schema = SCHEMA()
                          AND table_name = ?
                        """,
                Integer.class,
                tableName
        );
        return count != null && count > 0;
    }

    private void ensureBatchAccess(BatchRecord batch, User user) {
        if (canViewAllUploads(user) || Objects.equals(batch.uploadedBy(), user.getUserId())) {
            return;
        }
        throw new BusinessException(403, "当前账号无权查看该上传批次");
    }

    private DataUploadBatchResponse getBatch(Long uploadId) {
        return jdbcTemplate.queryForObject("""
                        %s
                        FROM data_upload_batches b
                        JOIN users u ON u.user_id = b.uploaded_by
                        LEFT JOIN users reviewer ON reviewer.user_id = b.reviewed_by
                        LEFT JOIN users syncer ON syncer.user_id = b.synced_by
                        WHERE b.upload_id = ?
                        """.formatted(batchSelectSql()),
                batchMapper(),
                uploadId
        );
    }

    private BatchRecord findBatchRecord(Long uploadId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT *
                            FROM data_upload_batches
                            WHERE upload_id = ?
                            """,
                    (rs, rowNum) -> new BatchRecord(
                            rs.getLong("upload_id"),
                            rs.getString("file_name"),
                            rs.getString("stored_file_path"),
                            rs.getLong("uploaded_by"),
                            rs.getString("status"),
                            (Long) rs.getObject("reviewed_by")
                    ),
                    uploadId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("上传批次不存在");
        }
    }

    private BatchRecord findBatchRecordForUpdate(Long uploadId) {
        try {
            return jdbcTemplate.queryForObject("""
                            SELECT *
                            FROM data_upload_batches
                            WHERE upload_id = ?
                            FOR UPDATE
                            """,
                    (rs, rowNum) -> new BatchRecord(
                            rs.getLong("upload_id"),
                            rs.getString("file_name"),
                            rs.getString("stored_file_path"),
                            rs.getLong("uploaded_by"),
                            rs.getString("status"),
                            (Long) rs.getObject("reviewed_by")
                    ),
                    uploadId
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new BusinessException("上传批次不存在");
        }
    }

    private RowMapper<DataUploadBatchResponse> batchMapper() {
        return (rs, rowNum) -> DataUploadBatchResponse.builder()
                .uploadId(rs.getLong("upload_id"))
                .fileName(rs.getString("file_name"))
                .status(rs.getString("status"))
                .uploadedBy(rs.getLong("uploaded_by"))
                .uploadedByName(rs.getString("uploaded_by_name"))
                .uploadedByRole(rs.getString("uploaded_by_role"))
                .totalRows(rs.getInt("total_rows"))
                .validRows(rs.getInt("valid_rows"))
                .errorRows(rs.getInt("error_rows"))
                .warningRows(rs.getInt("warning_rows"))
                .syncedRows(rs.getInt("synced_rows"))
                .duplicateMessage(rs.getString("duplicate_message"))
                .createdAt(toLocalDateTime(rs.getTimestamp("created_at")))
                .syncedAt(toLocalDateTime(rs.getTimestamp("synced_at")))
                .reviewedBy((Long) rs.getObject("reviewed_by"))
                .reviewedByName(rs.getString("reviewed_by_name"))
                .reviewedAt(toLocalDateTime(rs.getTimestamp("reviewed_at")))
                .reviewAction(rs.getString("review_action"))
                .reviewNote(rs.getString("review_note"))
                .syncedBy((Long) rs.getObject("synced_by"))
                .syncedByName(rs.getString("synced_by_name"))
                .build();
    }

    private String batchSelectSql() {
        return """
                SELECT b.*,
                       u.username AS uploaded_by_name,
                       u.role AS uploaded_by_role,
                       reviewer.username AS reviewed_by_name,
                       syncer.username AS synced_by_name
                """;
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isBlank() ? null : trimmed;
    }

    private RowMapper<DataUploadRowResponse> rowMapper() {
        return (rs, rowNum) -> DataUploadRowResponse.builder()
                .rowId(rs.getLong("row_id"))
                .sheetName(rs.getString("sheet_name"))
                .excelRowNumber(rs.getInt("excel_row_number"))
                .status(rs.getString("row_status"))
                .data(fromJsonMap(rs.getString("raw_json")))
                .errors(fromJsonList(rs.getString("error_json")))
                .warnings(fromJsonList(rs.getString("warning_json")))
                .syncedMeasurementId((Long) rs.getObject("synced_measurement_id"))
                .syncedEntityType(rs.getString("synced_entity_type"))
                .syncedEntityId((Long) rs.getObject("synced_entity_id"))
                .build();
    }

    private DataUploadRowResponse toRowResponse(Long rowId, ParsedRow row, Long syncedMeasurementId) {
        return DataUploadRowResponse.builder()
                .rowId(rowId)
                .sheetName(row.sheetName())
                .excelRowNumber(row.excelRowNumber())
                .status(row.status())
                .data(row.data())
                .errors(row.errors())
                .warnings(row.warnings())
                .syncedMeasurementId(syncedMeasurementId)
                .build();
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new BusinessException("JSON 序列化失败");
        }
    }

    private Map<String, String> fromJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
        } catch (IOException ex) {
            return Map.of();
        }
    }

    private List<String> fromJsonList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (IOException ex) {
            return List.of();
        }
    }

    private void tryRefreshMapStats(List<String> warnings) {
        if (!tableExists("map_pndl_stats") || !tableExists("geo_locations")) {
            warnings.add("地图聚合表尚未初始化，已跳过 map_pndl_stats 刷新");
            return;
        }
        try {
            ClassPathResource script = new ClassPathResource("db/map_pndl_stats_refresh_v2.sql");
            if (!script.exists()) {
                throw new BusinessException("未找到地图聚合刷新脚本");
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(script);
            populator.execute(dataSource);
        } catch (Exception ex) {
            throw new BusinessException("地图聚合刷新失败：" + ex.getMessage());
        }
    }

    private record StoredFile(Path path) {
    }

    private record StagedFile(Path path, String sha256) {
    }

    private record ParsedWorkbook(List<String> headerErrors, List<ParsedRow> rows) {
    }

    private record ParsedRow(String sheetName,
                             int excelRowNumber,
                             Map<String, String> data,
                             List<String> errors,
                             List<String> warnings) {
        String status() {
            if (!errors.isEmpty()) return ROW_STATUS_ERROR;
            if (!warnings.isEmpty()) return ROW_STATUS_WARNING;
            return ROW_STATUS_VALID;
        }

        boolean hasErrors() {
            return !errors.isEmpty();
        }

        boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    private record BatchRecord(Long uploadId, String fileName, String storedFilePath, Long uploadedBy,
                               String status, Long reviewedBy) {
    }
}
