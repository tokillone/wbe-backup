package com.licong.webbackup.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.licong.webbackup.dto.upload.DataUploadBatchPageResponse;
import com.licong.webbackup.dto.upload.DataUploadBatchResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadRowResponse;
import com.licong.webbackup.dto.upload.DataUploadRowsPageResponse;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static final int PREVIEW_LIMIT = 20;
    private static final Set<String> MANAGER_ROLES = Set.of("admin", "editor");
    private static final Set<String> PRESCRIPTION_TYPES = Set.of("处方药", "非处方药", "其他");
    private static final Pattern PREFERRED_NUMBER = Pattern.compile("取\\s*([+-]?\\d+(?:\\.\\d+)?)");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^([+-]?\\d+(?:\\.\\d+)?)");

    public static final List<String> REQUIRED_HEADERS = List.of(
            "文献编号",
            "目标类别",
            "目标物质类别",
            "目标物质子类",
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
    public static final List<String> OPTIONAL_HEADERS = List.of("来源工作簿说明", "原表行号说明");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final DataSource dataSource;

    public DataUploadService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.dataSource = dataSource;
    }

    public void requireManager(User user) {
        if (!isManager(user)) {
            throw new BusinessException(403, "当前账号无权执行该操作");
        }
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

    private boolean isManager(User user) {
        return user != null && user.getRole() != null && MANAGER_ROLES.contains(user.getRole());
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
            buildFieldGuideSheet(workbook, titleStyle, headerStyle, bodyStyle);
            buildInstructionSheet(workbook, titleStyle, headerStyle, bodyStyle);

            workbook.write(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException("生成 Excel 模板失败");
        }
    }

    @Transactional
    public DataUploadPreviewResponse preview(MultipartFile file, User user) {
        requireCanUpload(user);
        if (file == null || file.isEmpty()) {
            throw new BusinessException("请选择需要上传的 Excel 文件");
        }
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("upload.xlsx");
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new BusinessException("仅支持 .xlsx 文件");
        }

        byte[] bytes = readBytes(file);
        String sha256 = sha256(bytes);
        String duplicateMessage = findDuplicateMessage(sha256).orElse(null);
        StoredFile storedFile = storeFile(bytes, fileName);

        ParsedWorkbook parsedWorkbook;
        try {
            parsedWorkbook = parseWorkbook(bytes);
        } catch (BusinessException ex) {
            deleteStoredFile(storedFile);
            String failureMessage = truncate("文件解析失败：" + ex.getMessage(), 500);
            Long uploadId = insertBatch(
                    fileName,
                    null,
                    sha256,
                    user.getUserId(),
                    "VALIDATION_FAILED",
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
                    .build();
        }
        List<ParsedRow> rows = parsedWorkbook.rows();
        int errorRows = (int) rows.stream().filter(ParsedRow::hasErrors).count();
        int warningRows = (int) rows.stream().filter(ParsedRow::hasWarnings).count();
        int validRows = rows.size() - errorRows;
        String status;
        if (!parsedWorkbook.headerErrors().isEmpty() || errorRows > 0) {
            status = "VALIDATION_FAILED";
        } else if (canSyncData(user)) {
            status = "PREVIEWED";
        } else {
            status = "PENDING_REVIEW";
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
        for (ParsedRow row : rows) {
            Long rowId = insertUploadRow(uploadId, row);
            if (previewRows.size() < PREVIEW_LIMIT) {
                previewRows.add(toRowResponse(rowId, row, null));
            }
        }

        DataUploadBatchResponse batch = getBatch(uploadId);
        return DataUploadPreviewResponse.builder()
                .batch(batch)
                .requiredHeaders(REQUIRED_HEADERS)
                .optionalHeaders(OPTIONAL_HEADERS)
                .headerErrors(parsedWorkbook.headerErrors())
                .batchWarnings(batchWarnings)
                .previewRows(previewRows)
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
                where.append(" AND b.status = 'PENDING_REVIEW'");
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
                        ORDER BY excel_row_number ASC
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
        if (Boolean.FALSE.equals(user.getCanDownload())) {
            throw new BusinessException(403, "当前账号已被禁止下载文件");
        }
        BatchRecord batch = findBatchRecord(uploadId);
        ensureBatchAccess(batch, user);
        if (batch.storedFilePath() == null || batch.storedFilePath().isBlank()) {
            throw new BusinessException("原始文件不存在");
        }
        Path path = Path.of(batch.storedFilePath());
        if (!Files.exists(path)) {
            throw new BusinessException("原始文件已被移除");
        }
        return path;
    }

    public String getFileName(Long uploadId, User user) {
        requireCanViewUploads(user);
        if (Boolean.FALSE.equals(user.getCanDownload())) {
            throw new BusinessException(403, "当前账号已被禁止下载文件");
        }
        BatchRecord batch = findBatchRecord(uploadId);
        ensureBatchAccess(batch, user);
        return batch.fileName();
    }

    @Transactional
    public DataUploadSyncResponse sync(Long uploadId, User user) {
        requireCanSyncData(user);
        BatchRecord batch = findBatchRecord(uploadId);
        ensureBatchAccess(batch, user);
        boolean isPendingReview = "PENDING_REVIEW".equals(batch.status());
        if (!"PREVIEWED".equals(batch.status()) && !"PENDING_REVIEW".equals(batch.status())) {
            if ("SYNCED".equals(batch.status())) {
                throw new BusinessException("该批次已经同步入库");
            }
            throw new BusinessException("该批次存在阻断错误，不能同步入库");
        }
        if (isPendingReview && !canReviewUploads(user)) {
            throw new BusinessException(403, "待审核批次需要具备审核和同步权限后才能通过并入库");
        }

        List<DataUploadRowResponse> rows = jdbcTemplate.query("""
                        SELECT *
                        FROM data_upload_rows
                        WHERE upload_id = ? AND row_status <> 'ERROR'
                        ORDER BY excel_row_number ASC
                        """,
                rowMapper(),
                uploadId
        );
        int insertedRows = 0;
        int skippedRows = 0;
        List<String> syncWarnings = new ArrayList<>();

        for (DataUploadRowResponse row : rows) {
            try {
                Long measurementId = syncRow(row);
                if (measurementId == null) {
                    skippedRows += 1;
                    jdbcTemplate.update("""
                                    UPDATE data_upload_rows
                                    SET row_status = 'SKIPPED',
                                        warning_json = ?
                                    WHERE row_id = ?
                                    """,
                            toJson(mergeWarnings(row.getWarnings(), "该行与已入库记录重复，已跳过")),
                            row.getRowId()
                    );
                } else {
                    insertedRows += 1;
                    jdbcTemplate.update("""
                                    UPDATE data_upload_rows
                                    SET row_status = 'SYNCED',
                                        synced_measurement_id = ?
                                    WHERE row_id = ?
                                    """,
                            measurementId,
                            row.getRowId()
                    );
                }
            } catch (Exception ex) {
                throw new BusinessException("第 " + row.getExcelRowNumber() + " 行同步失败：" + ex.getMessage());
            }
        }

        if (isPendingReview) {
            jdbcTemplate.update("""
                            UPDATE data_upload_batches
                            SET status = 'SYNCED',
                                synced_rows = ?,
                                synced_at = NOW(),
                                synced_by = ?,
                                reviewed_by = ?,
                                reviewed_at = NOW(),
                                review_action = 'SYNCED'
                            WHERE upload_id = ?
                            """,
                    insertedRows,
                    user.getUserId(),
                    user.getUserId(),
                    uploadId
            );
        } else {
            jdbcTemplate.update("""
                            UPDATE data_upload_batches
                            SET status = 'SYNCED',
                                synced_rows = ?,
                                synced_at = NOW(),
                                synced_by = ?
                            WHERE upload_id = ?
                            """,
                    insertedRows,
                    user.getUserId(),
                    uploadId
            );
        }

        tryRefreshMapStats(syncWarnings);
        return DataUploadSyncResponse.builder()
                .batch(getBatch(uploadId))
                .insertedRows(insertedRows)
                .skippedRows(skippedRows)
                .warnings(syncWarnings)
                .build();
    }

    @Transactional
    public DataUploadBatchResponse reject(Long uploadId, User user, String reason) {
        requireCanReviewUploads(user);
        BatchRecord batch = findBatchRecord(uploadId);
        if (!"PENDING_REVIEW".equals(batch.status())) {
            if ("SYNCED".equals(batch.status())) {
                throw new BusinessException("已入库批次不能驳回");
            }
            if ("REJECTED".equals(batch.status())) {
                throw new BusinessException("该批次已经驳回");
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
                        SET status = 'REJECTED',
                            reviewed_by = ?,
                            reviewed_at = NOW(),
                            review_action = 'REJECTED',
                            review_note = ?
                        WHERE upload_id = ?
                        """,
                user.getUserId(),
                reviewNote,
                uploadId
        );
        return getBatch(uploadId);
    }

    private Long syncRow(DataUploadRowResponse row) throws IOException {
        Map<String, String> data = row.getData();
        Long compoundId = getOrCreateCompound(data);
        if (isExistingBusinessRecord(data, compoundId)) {
            return null;
        }
        Long methodId = getOrCreateMethod(data);
        Long plantId = getOrCreatePlant(data);
        Long eventId = insertSamplingEvent(data, plantId);
        return insertMeasurement(data, compoundId, methodId, eventId);
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
            setCell(row, 2, "sampling_events / 上传审计", bodyStyle);
            setCell(row, 3, "用于重复检查和来源追踪；没有可留空。", bodyStyle);
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
                new String[]{"格式要求", "表头规则", "“数据表”第 1 行前 58 列必须与字段清单完全一致；不要调整顺序。"},
                new String[]{"格式要求", "可选列", "可保留“来源工作簿说明 / 原表行号说明”，用于重复检查和审计。"},
                new String[]{"数据规则", "NA / ND / <LOD / <LOQ", "系统会保留原始值；可入 DECIMAL 的部分入库，无法入库的数值列写入 NULL 并给出警告。"},
                new String[]{"同步规则", "预览校验", "上传后先生成批次、错误、警告和前 20 行预览；无阻断错误时才能同步入库。"},
                new String[]{"同步规则", "重复控制", "系统会先提示相同 SHA256 文件，再按来源工作簿、原表行号和目标物做入库重复检查。"}
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

    private Long getOrCreateCompound(Map<String, String> data) {
        String biomarkerCas = valueOrNull(data.get("生物标记物CAS"));
        String drugName = firstUseful(data.get("药物"), data.get("生物标记物名称"), data.get("biomarker"), "NA");
        Long existing = queryOptionalLong("""
                        SELECT compound_id
                        FROM compounds
                        WHERE drug_name = ?
                          AND ((biomarker_cas IS NULL AND ? IS NULL) OR biomarker_cas = ?)
                        LIMIT 1
                        """,
                drugName,
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                valueOrFallback(data.get("目标类别"), "NA"),
                valueOrFallback(data.get("目标物质类别"), "NA"),
                valueOrNull(data.get("目标物质子类")),
                drugName,
                valueOrNull(data.get("适应症")),
                safePrescriptionType(data.get("处方/非处方")),
                valueOrNull(data.get("生物标记物名称")),
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
        Long existing = queryOptionalLong("""
                        SELECT method_id
                        FROM analytical_methods
                        WHERE sampling_method = ? AND analysis_method = ?
                        LIMIT 1
                        """,
                samplingMethod,
                analysisMethod
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
                parseDecimalForDatabase(data.get("MDL_value")),
                valueOrNull(data.get("MDL_unit")),
                parseDecimalForDatabase(data.get("MQL_value")),
                valueOrNull(data.get("MQL_unit")),
                parseDecimalForDatabase(data.get("IDL_value")),
                valueOrNull(data.get("IDL_unit")),
                parseDecimalForDatabase(data.get("IQL_value")),
                valueOrNull(data.get("IQL_unit"))
        );
    }

    private Long getOrCreatePlant(Map<String, String> data) {
        String plantName = valueOrFallback(data.get("污水厂名称"), "NA");
        Long existing = queryOptionalLong("""
                        SELECT plant_id
                        FROM wastewater_plants
                        WHERE plant_name = ?
                        LIMIT 1
                        """,
                plantName
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
                valueOrNull(data.get("污水厂位置_国")),
                valueOrNull(data.get("污水厂位置_省")),
                valueOrNull(data.get("污水厂位置_市")),
                parseDecimalForDatabase(data.get("GS管道衰减系数"))
        );
    }

    private Long insertSamplingEvent(Map<String, String> data, Long plantId) {
        return insertAndReturnKey("""
                        INSERT INTO sampling_events (
                            plant_id,
                            sample_collection_time,
                            sampling_start_ym,
                            sampling_end_ym,
                            source_workbook,
                            original_row_number
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                plantId,
                null,
                valueOrNull(data.get("采样开始时间_YYYY_MM")),
                valueOrNull(data.get("采样结束时间_YYYY_MM")),
                valueOrNull(data.get("来源工作簿说明")),
                parseIntegerForDatabase(data.get("原表行号说明"))
        );
    }

    private Long insertMeasurement(Map<String, String> data, Long compoundId, Long methodId, Long eventId) {
        return insertAndReturnKey("""
                        INSERT INTO measurements (
                            compound_id,
                            method_id,
                            event_id,
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
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                compoundId,
                methodId,
                eventId,
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

    private boolean isExistingBusinessRecord(Map<String, String> data, Long compoundId) {
        String sourceWorkbook = valueOrNull(data.get("来源工作簿说明"));
        Integer originalRowNumber = parseIntegerForDatabase(data.get("原表行号说明"));
        if (sourceWorkbook == null || originalRowNumber == null) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM measurements m
                        JOIN sampling_events se ON se.event_id = m.event_id
                        WHERE m.compound_id = ?
                          AND se.source_workbook = ?
                          AND se.original_row_number = ?
                        """,
                Integer.class,
                compoundId,
                sourceWorkbook,
                originalRowNumber
        );
        return count != null && count > 0;
    }

    private ParsedWorkbook parseWorkbook(byte[] bytes) {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = workbook.getSheet(DATA_SHEET_NAME);
            if (sheet == null) {
                return new ParsedWorkbook(List.of("上传文件必须包含名为“数据表”的工作表"), List.of());
            }
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<String> headerErrors = validateHeaders(sheet, formatter);
            if (!headerErrors.isEmpty()) {
                return new ParsedWorkbook(headerErrors, List.of());
            }
            List<ParsedRow> rows = new ArrayList<>();
            int lastRowNum = sheet.getLastRowNum();
            for (int rowIndex = 1; rowIndex <= lastRowNum; rowIndex++) {
                Row sheetRow = sheet.getRow(rowIndex);
                Map<String, String> data = readRow(sheetRow, formatter);
                if (isBlankRow(data)) {
                    continue;
                }
                rows.add(validateRow(rowIndex + 1, data));
            }
            return new ParsedWorkbook(List.of(), rows);
        } catch (Exception ex) {
            throw new BusinessException("Excel 解析失败：" + ex.getMessage());
        }
    }

    private List<String> validateHeaders(Sheet sheet, DataFormatter formatter) {
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return List.of("数据表第一行必须是表头");
        }
        List<String> errors = new ArrayList<>();
        for (int i = 0; i < REQUIRED_HEADERS.size(); i++) {
            String actual = normalizeCell(formatter.formatCellValue(headerRow.getCell(i)));
            String expected = REQUIRED_HEADERS.get(i);
            if (!expected.equals(actual)) {
                errors.add("第 " + (i + 1) + " 列应为“" + expected + "”，实际为“" + blankForDisplay(actual) + "”");
            }
        }
        return errors;
    }

    private Map<String, String> readRow(Row row, DataFormatter formatter) {
        Map<String, String> data = new LinkedHashMap<>();
        List<String> headers = new ArrayList<>(REQUIRED_HEADERS);
        headers.addAll(OPTIONAL_HEADERS);
        for (int i = 0; i < headers.size(); i++) {
            String value = row == null ? "" : normalizeCell(formatter.formatCellValue(row.getCell(i)));
            data.put(headers.get(i), value);
        }
        return data;
    }

    private ParsedRow validateRow(int excelRowNumber, Map<String, String> data) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
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
            warnings.add("污水厂名称为空或 NA，入库时将按当前设计使用 NA 作为站点名称");
        }
        validateDecimalFields(data, warnings);
        return new ParsedRow(excelRowNumber, rowStatus(errors, warnings), data, errors, warnings);
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

    private String safePrescriptionType(String value) {
        String normalized = valueOrNull(value);
        if (normalized == null || !PRESCRIPTION_TYPES.contains(normalized)) {
            return null;
        }
        return normalized;
    }

    private String rowStatus(List<String> errors, List<String> warnings) {
        if (!errors.isEmpty()) {
            return "ERROR";
        }
        if (!warnings.isEmpty()) {
            return "WARNING";
        }
        return "VALID";
    }

    private void requireUseful(Map<String, String> data, String field, List<String> errors) {
        if (!isUseful(data.get(field))) {
            errors.add(field + " 不能为空或 NA");
        }
    }

    private boolean isBlankRow(Map<String, String> data) {
        return REQUIRED_HEADERS.stream().allMatch(header -> normalizeCell(data.get(header)).isBlank());
    }

    private String normalizeCell(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isUseful(String value) {
        String normalized = normalizeCell(value);
        return !normalized.isBlank() && !isNaToken(normalized);
    }

    private boolean isNaToken(String value) {
        String normalized = normalizeCell(value);
        return normalized.equalsIgnoreCase("NA") || normalized.equalsIgnoreCase("N/A") || normalized.equals("/");
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

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new BusinessException("读取上传文件失败");
        }
    }

    private StoredFile storeFile(byte[] bytes, String fileName) {
        try {
            Path uploadDir = Path.of(System.getProperty("user.dir"), "uploads", "data");
            Files.createDirectories(uploadDir);
            String safeName = UUID.randomUUID() + "_" + fileName.replaceAll("[\\\\/]+", "_");
            Path path = uploadDir.resolve(safeName);
            Files.write(path, bytes);
            return new StoredFile(path);
        } catch (IOException ex) {
            throw new BusinessException("保存上传文件失败");
        }
    }

    private void deleteStoredFile(StoredFile storedFile) {
        if (storedFile == null || storedFile.path() == null) {
            return;
        }
        try {
            Files.deleteIfExists(storedFile.path());
        } catch (IOException ignored) {
            // The failed upload is still audited even if the temporary file cleanup cannot complete.
        }
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
                            excel_row_number,
                            row_status,
                            raw_json,
                            error_json,
                            warning_json
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                uploadId,
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
                            rs.getString("status")
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
                .excelRowNumber(rs.getInt("excel_row_number"))
                .status(rs.getString("row_status"))
                .data(fromJsonMap(rs.getString("raw_json")))
                .errors(fromJsonList(rs.getString("error_json")))
                .warnings(fromJsonList(rs.getString("warning_json")))
                .syncedMeasurementId((Long) rs.getObject("synced_measurement_id"))
                .build();
    }

    private DataUploadRowResponse toRowResponse(Long rowId, ParsedRow row, Long syncedMeasurementId) {
        return DataUploadRowResponse.builder()
                .rowId(rowId)
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

    private List<String> mergeWarnings(List<String> current, String extra) {
        List<String> merged = new ArrayList<>(current == null ? List.of() : current);
        merged.add(extra);
        return merged;
    }

    private void tryRefreshMapStats(List<String> warnings) {
        try {
            ClassPathResource script = new ClassPathResource("db/map_pndl_stats_refresh.sql");
            if (!script.exists()) {
                warnings.add("未找到地图聚合刷新脚本，已跳过 map_pndl_stats 刷新");
                return;
            }
            ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
            populator.addScript(script);
            populator.execute(dataSource);
        } catch (Exception ex) {
            warnings.add("数据已入库，但地图聚合刷新失败：" + ex.getMessage());
        }
    }

    private record StoredFile(Path path) {
    }

    private record ParsedWorkbook(List<String> headerErrors, List<ParsedRow> rows) {
    }

    private record ParsedRow(int excelRowNumber,
                             String status,
                             Map<String, String> data,
                             List<String> errors,
                             List<String> warnings) {
        boolean hasErrors() {
            return !errors.isEmpty();
        }

        boolean hasWarnings() {
            return !warnings.isEmpty();
        }
    }

    private record BatchRecord(Long uploadId, String fileName, String storedFilePath, Long uploadedBy, String status) {
    }
}
