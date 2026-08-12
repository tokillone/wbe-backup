package com.licong.webbackup.service;

import com.licong.webbackup.exception.BusinessException;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Comment;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Year;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 单表投稿与五表审核工作簿契约。这里不负责数据库写入，所有原始值都以文本形式保留。
 */
public final class SimplifiedUploadWorkbook {

    public static final String SUBMISSION_SHEET = "原始数据";
    public static final String NORMALIZED_SHEET = "规范数据记录";
    public static final String LITERATURE_SHEET = "文献基础信息";
    public static final String SITE_SHEET = "点位关联表";
    public static final String METHOD_SHEET = "采样方法审计";
    public static final String ICD11_SHEET = "ICD11映射";

    public static final List<String> SUBMISSION_HEADERS = List.of(
            "投稿行ID", "投稿类型", "已有文献编号", "DOI", "文献标题", "发表年份", "期刊/来源",
            "keywords", "abstract", "来源文件名或URL", "来源记录编号", "目标物/药物原文", "适应症原文",
            "处方属性原文", "生物标记物名称原文", "biomarker英文原文", "CAS原文", "理化性质原文",
            "采样方法原文", "分析方法原文", "点位类型", "点位名称原文", "污水厂处理规模_m3_day",
            "汇水区人口", "国家原文", "省州原文", "城市原文", "样品采集时间原文", "指标类型",
            "统计量", "原始数值", "原始单位", "数值来源", "计算换算说明", "页码表号Sheet图号",
            "原文证据", "备注"
    );

    public static final List<String> NORMALIZED_HEADERS = List.of(
            "审核记录ID", "投稿行ID", "记录组ID", "文献候选ID", "文献编号", "来源记录编号",
            "目标物药物原文", "适应症原文", "处方属性原文", "生物标记物名称原文", "biomarker英文原文",
            "CAS原文", "理化性质原文", "采样方法原文", "分析方法原文", "点位类型原文", "点位名称原文",
            "国家原文", "省州原文", "城市原文", "样品采集时间原文", "指标类型原文", "统计量原文",
            "原始数值", "原始单位", "数值来源原文", "来源定位原文", "原文证据",
            "目标类别", "目标物质类别", "目标物质子类", "目标物质细类", "标准药物名称", "标准适应症",
            "标准处方属性", "标准生物标记物名称", "标准biomarker英文", "标准CAS", "数值限定符",
            "标准数值", "标准单位", "采样开始年月", "采样结束年月", "记录处置", "排除原因", "纠正原因"
    );

    public static final List<String> LITERATURE_HEADERS = List.of(
            "文献候选ID", "候选文献编号", "匹配决定", "已有文献编号", "标准DOI", "文献标题", "发表年份",
            "期刊/来源", "keywords", "abstract", "来源文件名或URL", "审核结论", "备注"
    );

    public static final List<String> SITE_HEADERS = List.of(
            "点位审核ID", "记录组ID", "文献编号", "报告点位键", "点位类型原文", "点位名称原文", "国家原文",
            "省州原文", "城市原文", "处理规模原文", "汇水区人口原文", "标准点位名称", "标准国家", "标准省州",
            "标准城市", "已有点位ID", "是否计入统计", "关联说明", "确认依据", "审核结论"
    );

    public static final List<String> METHOD_HEADERS = List.of(
            "方法审核ID", "文献候选ID", "文献编号", "原始方法哈希", "原始采样方法", "原始分析方法",
            "规范化采样方法明细", "标准采样方法", "标准分析方法", "采样主类", "采样对象", "比例方式",
            "采样部署时长", "被动采样器类型", "站点对应状态", "来源文件", "来源定位", "原文证据",
            "审核结论", "标准化处理说明", "备注"
    );

    public static final List<String> ICD11_HEADERS = List.of(
            "映射审核ID", "来源适应症组ID", "文献候选ID", "文献编号", "目标类别", "目标物质类别",
            "目标物质子类", "目标物质细类", "标准药物名称", "标准生物标记物名称", "标准适应症", "疾病实体",
            "ICD11一级编码", "ICD11一级名称", "ICD11二级编码", "ICD11二级名称", "ICD11三级编码",
            "ICD11三级名称", "映射层级", "匹配类型", "是否进入Sankey", "排除原因", "证据", "审核结论", "备注"
    );

    public static final List<String> REVIEW_SHEETS = List.of(
            NORMALIZED_SHEET, LITERATURE_SHEET, SITE_SHEET, METHOD_SHEET, ICD11_SHEET
    );

    private static final Set<String> REQUIRED_SUBMISSION = Set.of(
            "投稿类型", "文献标题", "发表年份", "期刊/来源", "来源文件名或URL", "来源记录编号",
            "生物标记物名称原文", "采样方法原文", "分析方法原文", "点位类型", "点位名称原文", "国家原文",
            "样品采集时间原文", "指标类型", "统计量", "原始数值", "原始单位", "数值来源",
            "页码表号Sheet图号", "原文证据"
    );
    private static final Set<String> OPTIONAL_SUBMISSION = Set.of(
            "已有文献编号", "DOI", "keywords", "abstract", "目标物/药物原文", "适应症原文", "处方属性原文",
            "biomarker英文原文", "CAS原文", "理化性质原文", "污水厂处理规模_m3_day", "汇水区人口",
            "省州原文", "城市原文", "计算换算说明", "备注"
    );

    private static final Map<String, List<String>> ENUMS = Map.of(
            "投稿类型", List.of("新文献", "补充已有文献"),
            "点位类型", List.of("污水厂", "管网点位", "城市或区域汇总", "无法拆分", "其他"),
            "统计量", List.of("直接值", "min", "max", "average", "median"),
            "数值来源", List.of("文献直接报告", "文献内计算或换算", "投稿人计算"),
            "指标类型", List.of("MDL", "MQL", "IDL", "IQL", "进水浓度", "每日质量负荷DLs", "PNDL直接值",
                    "校准系数", "GS管道衰减系数", "人体排泄率", "药物消费量", "药物使用流行率", "疾病患病率")
    );

    private SimplifiedUploadWorkbook() {
    }

    public static byte[] createSubmissionTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SUBMISSION_SHEET);
            CellStyle systemStyle = headerStyle(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.BLACK);
            CellStyle requiredStyle = headerStyle(workbook, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_BLUE);
            CellStyle optionalStyle = headerStyle(workbook, IndexedColors.WHITE, IndexedColors.DARK_BLUE);
            Row header = sheet.createRow(0);
            header.setHeightInPoints(42);
            for (int i = 0; i < SUBMISSION_HEADERS.size(); i++) {
                String field = SUBMISSION_HEADERS.get(i);
                Cell cell = header.createCell(i, CellType.STRING);
                cell.setCellValue(field);
                cell.setCellStyle("投稿行ID".equals(field) ? systemStyle
                        : REQUIRED_SUBMISSION.contains(field) ? requiredStyle : optionalStyle);
                addHeaderComment(workbook, sheet, cell, fieldHelp(field));
                int width = field.contains("原文") || field.contains("证据") || field.contains("abstract")
                        ? 28 : Math.max(13, Math.min(24, field.length() * 2 + 4));
                sheet.setColumnWidth(i, width * 256);
            }
            sheet.createFreezePane(1, 1);
            sheet.setAutoFilter(new CellRangeAddress(0, 0, 0, SUBMISSION_HEADERS.size() - 1));
            sheet.setDisplayGridlines(false);
            addSubmissionValidations(sheet);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("生成投稿模板失败");
        }
    }

    public static ParsedSubmission parseSubmission(Path path, Set<String> allowedIds) {
        try (Workbook workbook = new XSSFWorkbook(path.toFile())) {
            List<String> workbookErrors = validateExactSheets(workbook, List.of(SUBMISSION_SHEET));
            Sheet sheet = workbook.getSheet(SUBMISSION_SHEET);
            if (sheet == null) return new ParsedSubmission(workbookErrors, List.of());
            workbookErrors.addAll(validateHeader(sheet, SUBMISSION_HEADERS));
            if (!workbookErrors.isEmpty()) return new ParsedSubmission(workbookErrors, List.of());
            rejectFormulasAndHiddenData(workbook, sheet, workbookErrors);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            List<SubmissionRow> rows = new ArrayList<>();
            Set<String> seenIds = new LinkedHashSet<>();
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row excelRow = sheet.getRow(r);
                Map<String, String> values = readRow(excelRow, SUBMISSION_HEADERS, formatter);
                if (isBlank(values.values())) continue;
                List<String> errors = validateSubmissionRow(values);
                String rowId = trim(values.get("投稿行ID"));
                if (rowId.isBlank()) {
                    rowId = "SR-" + UUID.randomUUID();
                    values.put("投稿行ID", rowId);
                } else if (allowedIds != null && !allowedIds.contains(rowId)) {
                    errors.add("投稿行ID不属于该批次；新增行请留空由系统生成");
                }
                if (!seenIds.add(rowId)) errors.add("投稿行ID在文件中重复");
                rows.add(new SubmissionRow(r + 1, rowId, values, errors));
            }
            if (rows.isEmpty()) workbookErrors.add("原始数据没有可处理的数据行");
            return new ParsedSubmission(workbookErrors, rows);
        } catch (IOException | InvalidFormatException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) throw businessException;
            throw new BusinessException("无法读取投稿工作簿，请确认文件为有效的无宏 .xlsx 文件");
        }
    }

    public static byte[] createNormalizedSubmissionWorkbook(List<SubmissionRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(SUBMISSION_SHEET);
            writeSheet(workbook, sheet, SUBMISSION_HEADERS,
                    rows.stream().map(SubmissionRow::values).toList(), Set.of("投稿行ID"), REQUIRED_SUBMISSION);
            addSubmissionValidations(sheet);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("生成带投稿行ID的工作簿失败");
        }
    }

    public static byte[] createReviewDraft(List<SubmissionRow> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            List<Map<String, String>> normalized = new ArrayList<>();
            Map<String, Map<String, String>> literatures = new LinkedHashMap<>();
            Map<String, Map<String, String>> sites = new LinkedHashMap<>();
            Map<String, Map<String, String>> methods = new LinkedHashMap<>();
            Map<String, Map<String, String>> indications = new LinkedHashMap<>();
            for (SubmissionRow row : rows) {
                Map<String, String> raw = row.values();
                String candidateId = literatureCandidateId(raw);
                String groupId = recordGroupId(candidateId, raw);
                String methodHash = shortHash(trim(raw.get("采样方法原文")) + "\u001f" + trim(raw.get("分析方法原文")));
                String indicationId = "IND-" + shortHash(candidateId + "\u001f" + trim(raw.get("适应症原文")));

                Map<String, String> item = blankRow(NORMALIZED_HEADERS);
                put(item, "审核记录ID", "RV-" + row.submissionRowId());
                put(item, "投稿行ID", row.submissionRowId());
                put(item, "记录组ID", groupId);
                put(item, "文献候选ID", candidateId);
                put(item, "文献编号", raw.get("已有文献编号"));
                copy(item, raw, Map.ofEntries(
                        Map.entry("来源记录编号", "来源记录编号"), Map.entry("目标物药物原文", "目标物/药物原文"),
                        Map.entry("适应症原文", "适应症原文"), Map.entry("处方属性原文", "处方属性原文"),
                        Map.entry("生物标记物名称原文", "生物标记物名称原文"), Map.entry("biomarker英文原文", "biomarker英文原文"),
                        Map.entry("CAS原文", "CAS原文"), Map.entry("理化性质原文", "理化性质原文"),
                        Map.entry("采样方法原文", "采样方法原文"), Map.entry("分析方法原文", "分析方法原文"),
                        Map.entry("点位类型原文", "点位类型"), Map.entry("点位名称原文", "点位名称原文"),
                        Map.entry("国家原文", "国家原文"), Map.entry("省州原文", "省州原文"),
                        Map.entry("城市原文", "城市原文"), Map.entry("样品采集时间原文", "样品采集时间原文"),
                        Map.entry("指标类型原文", "指标类型"), Map.entry("统计量原文", "统计量"),
                        Map.entry("原始数值", "原始数值"), Map.entry("原始单位", "原始单位"),
                        Map.entry("数值来源原文", "数值来源"), Map.entry("来源定位原文", "页码表号Sheet图号"),
                        Map.entry("原文证据", "原文证据")
                ));
                put(item, "标准药物名称", raw.get("目标物/药物原文"));
                put(item, "标准适应症", raw.get("适应症原文"));
                put(item, "标准处方属性", raw.get("处方属性原文"));
                put(item, "标准生物标记物名称", raw.get("生物标记物名称原文"));
                put(item, "标准biomarker英文", raw.get("biomarker英文原文"));
                put(item, "标准CAS", raw.get("CAS原文"));
                put(item, "标准数值", parseableNumber(raw.get("原始数值")) ? trim(raw.get("原始数值")) : "");
                put(item, "标准单位", raw.get("原始单位"));
                put(item, "记录处置", "发布");
                normalized.add(item);

                literatures.computeIfAbsent(candidateId, ignored -> literatureDraft(candidateId, raw));
                sites.putIfAbsent(groupId, siteDraft(groupId, raw));
                methods.putIfAbsent(candidateId + ":" + methodHash, methodDraft(candidateId, methodHash, raw));
                if (!trim(raw.get("适应症原文")).isBlank()) {
                    indications.putIfAbsent(indicationId, indicationDraft(indicationId, candidateId, raw));
                }
            }
            writeReviewSheet(workbook, NORMALIZED_SHEET, NORMALIZED_HEADERS, normalized,
                    Set.of("审核记录ID", "投稿行ID", "记录组ID", "文献候选ID", "文献编号", "来源记录编号",
                            "目标物药物原文", "适应症原文", "处方属性原文", "生物标记物名称原文", "biomarker英文原文",
                            "CAS原文", "理化性质原文", "采样方法原文", "分析方法原文", "点位类型原文", "点位名称原文",
                            "国家原文", "省州原文", "城市原文", "样品采集时间原文", "指标类型原文", "统计量原文",
                            "原始数值", "原始单位", "数值来源原文", "来源定位原文", "原文证据"));
            writeReviewSheet(workbook, LITERATURE_SHEET, LITERATURE_HEADERS, literatures.values(), Set.of("文献候选ID", "候选文献编号"));
            writeReviewSheet(workbook, SITE_SHEET, SITE_HEADERS, sites.values(), Set.of("点位审核ID", "记录组ID", "文献编号", "报告点位键", "点位类型原文", "点位名称原文", "国家原文", "省州原文", "城市原文", "处理规模原文", "汇水区人口原文"));
            writeReviewSheet(workbook, METHOD_SHEET, METHOD_HEADERS, methods.values(), Set.of("方法审核ID", "文献候选ID", "文献编号", "原始方法哈希", "原始采样方法", "原始分析方法"));
            writeReviewSheet(workbook, ICD11_SHEET, ICD11_HEADERS, indications.values(), Set.of("映射审核ID", "来源适应症组ID", "文献候选ID", "文献编号"));
            addReviewValidations(workbook);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new BusinessException("生成五表审核草稿失败");
        }
    }

    public static ParsedReview parseReview(Path path) {
        try (Workbook workbook = new XSSFWorkbook(path.toFile())) {
            List<String> errors = validateExactSheets(workbook, REVIEW_SHEETS);
            Map<String, List<ReviewRow>> rows = new LinkedHashMap<>();
            Map<String, List<String>> contracts = Map.of(
                    NORMALIZED_SHEET, NORMALIZED_HEADERS, LITERATURE_SHEET, LITERATURE_HEADERS,
                    SITE_SHEET, SITE_HEADERS, METHOD_SHEET, METHOD_HEADERS, ICD11_SHEET, ICD11_HEADERS);
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            for (String sheetName : REVIEW_SHEETS) {
                Sheet sheet = workbook.getSheet(sheetName);
                if (sheet == null) {
                    rows.put(sheetName, List.of());
                    continue;
                }
                List<String> headerErrors = validateHeader(sheet, contracts.get(sheetName));
                errors.addAll(headerErrors.stream().map(message -> sheetName + "：" + message).toList());
                rejectFormulasAndHiddenData(workbook, sheet, errors);
                List<ReviewRow> sheetRows = new ArrayList<>();
                if (headerErrors.isEmpty()) {
                    for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                        Map<String, String> values = readRow(sheet.getRow(r), contracts.get(sheetName), formatter);
                        if (!isBlank(values.values())) sheetRows.add(new ReviewRow(sheetName, r + 1, values, new ArrayList<>()));
                    }
                }
                rows.put(sheetName, sheetRows);
            }
            return new ParsedReview(errors, rows);
        } catch (IOException | InvalidFormatException | RuntimeException exception) {
            if (exception instanceof BusinessException businessException) throw businessException;
            throw new BusinessException("无法读取审核工作簿，请确认文件为有效的无宏 .xlsx 文件");
        }
    }

    public static String literatureCandidateId(Map<String, String> raw) {
        String doi = normalizeDoi(raw.get("DOI"));
        String canonical = doi.isBlank()
                ? String.join("\u001f", normalize(raw.get("文献标题")), trim(raw.get("发表年份")), normalize(raw.get("期刊/来源")), normalize(raw.get("来源文件名或URL")))
                : doi;
        return "LITC-" + shortHash(canonical);
    }

    public static String recordGroupId(String candidateId, Map<String, String> raw) {
        return "RG-" + shortHash(candidateId + "\u001f" + normalize(raw.get("来源记录编号")) + "\u001f" + normalize(raw.get("生物标记物名称原文")));
    }

    public static String shortHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest).substring(0, 16).toUpperCase(Locale.ROOT);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static List<String> validateSubmissionRow(Map<String, String> values) {
        List<String> errors = new ArrayList<>();
        for (String field : REQUIRED_SUBMISSION) if (trim(values.get(field)).isBlank()) errors.add(field + "不能为空");
        for (Map.Entry<String, List<String>> entry : ENUMS.entrySet()) {
            String value = trim(values.get(entry.getKey()));
            if (!value.isBlank() && !entry.getValue().contains(value)) errors.add(entry.getKey() + "不是允许值");
        }
        String doi = normalizeDoi(values.get("DOI"));
        String type = trim(values.get("投稿类型"));
        if ("补充已有文献".equals(type) && doi.isBlank() && trim(values.get("已有文献编号")).isBlank()) {
            errors.add("补充已有文献时必须填写DOI或已有文献编号");
        }
        if (doi.isBlank() && (trim(values.get("文献标题")).isBlank() || trim(values.get("发表年份")).isBlank()
                || trim(values.get("期刊/来源")).isBlank() || trim(values.get("来源文件名或URL")).isBlank())) {
            errors.add("无DOI时必须提供标题、发表年份、期刊/来源和来源文件名或URL");
        }
        String year = trim(values.get("发表年份"));
        if (!year.matches("\\d{4}") || Integer.parseInt(year) < 1800 || Integer.parseInt(year) > Year.now().getValue() + 1) {
            errors.add("发表年份必须是合理的四位年份");
        }
        if (!"文献直接报告".equals(trim(values.get("数值来源"))) && trim(values.get("计算换算说明")).isBlank()) {
            errors.add("非文献直接报告的数据必须填写计算换算说明");
        }
        for (String numeric : List.of("污水厂处理规模_m3_day", "汇水区人口")) {
            String value = trim(values.get(numeric));
            if (!value.isBlank() && !value.matches("\\d+(?:\\.\\d+)?")) errors.add(numeric + "必须是非负数值");
        }
        return errors;
    }

    private static List<String> validateExactSheets(Workbook workbook, List<String> expected) {
        List<String> errors = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) actual.add(workbook.getSheetName(i));
        if (!actual.equals(expected)) errors.add("工作表必须且只能按顺序包含：" + String.join("、", expected));
        return errors;
    }

    private static List<String> validateHeader(Sheet sheet, List<String> expected) {
        Row header = sheet.getRow(0);
        if (header == null) return List.of("缺少表头行");
        DataFormatter formatter = new DataFormatter(Locale.CHINA);
        List<String> actual = new ArrayList<>();
        for (int i = 0; i < expected.size(); i++) actual.add(trim(formatter.formatCellValue(header.getCell(i))));
        if (!actual.equals(expected) || header.getLastCellNum() != expected.size()) return List.of("表头名称、顺序或列数与模板不一致");
        return List.of();
    }

    private static void rejectFormulasAndHiddenData(Workbook workbook, Sheet sheet, List<String> errors) {
        if (workbook.isSheetHidden(workbook.getSheetIndex(sheet)) || workbook.isSheetVeryHidden(workbook.getSheetIndex(sheet))) {
            errors.add(sheet.getSheetName() + "：业务工作表不能隐藏");
        }
        for (int c = 0; c < Math.max(0, sheet.getRow(0) == null ? 0 : sheet.getRow(0).getLastCellNum()); c++) {
            if (sheet.isColumnHidden(c)) errors.add(sheet.getSheetName() + "：不能隐藏业务列");
        }
        for (Row row : sheet) for (Cell cell : row) {
            if (cell.getCellType() == CellType.FORMULA) {
                errors.add(sheet.getSheetName() + "第" + (row.getRowNum() + 1) + "行含公式，上传文件只允许静态值");
                return;
            }
        }
    }

    private static Map<String, String> readRow(Row row, List<String> headers, DataFormatter formatter) {
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) values.put(headers.get(i), row == null ? "" : trim(formatter.formatCellValue(row.getCell(i))));
        return values;
    }

    private static void writeReviewSheet(Workbook workbook, String name, List<String> headers,
                                         Collection<Map<String, String>> rows, Set<String> locked) {
        writeSheet(workbook, workbook.createSheet(name), headers, rows, locked, Set.of());
    }

    private static void writeSheet(Workbook workbook, Sheet sheet, List<String> headers,
                                   Collection<Map<String, String>> rows, Set<String> locked, Set<String> required) {
        CellStyle lockedStyle = headerStyle(workbook, IndexedColors.GREY_25_PERCENT, IndexedColors.BLACK);
        CellStyle requiredStyle = headerStyle(workbook, IndexedColors.LIGHT_YELLOW, IndexedColors.DARK_BLUE);
        CellStyle editableStyle = headerStyle(workbook, IndexedColors.WHITE, IndexedColors.DARK_BLUE);
        Row header = sheet.createRow(0);
        header.setHeightInPoints(38);
        for (int i = 0; i < headers.size(); i++) {
            String field = headers.get(i);
            Cell cell = header.createCell(i, CellType.STRING);
            cell.setCellValue(field);
            cell.setCellStyle(locked.contains(field) ? lockedStyle : required.contains(field) ? requiredStyle : editableStyle);
            sheet.setColumnWidth(i, Math.min(32, Math.max(13, field.length() * 2 + 4)) * 256);
        }
        int rowIndex = 1;
        for (Map<String, String> values : rows) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < headers.size(); i++) row.createCell(i, CellType.STRING).setCellValue(trim(values.get(headers.get(i))));
        }
        sheet.createFreezePane(locked.isEmpty() ? 0 : 1, 1);
        sheet.setAutoFilter(new CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.size() - 1));
        sheet.setDisplayGridlines(false);
    }

    private static CellStyle headerStyle(Workbook workbook, IndexedColors fill, IndexedColors fontColor) {
        CellStyle style = workbook.createCellStyle();
        style.setFillForegroundColor(fill.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setWrapText(true);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(fontColor.getIndex());
        style.setFont(font);
        return style;
    }

    private static void addSubmissionValidations(Sheet sheet) {
        for (Map.Entry<String, List<String>> entry : ENUMS.entrySet()) addListValidation(sheet, SUBMISSION_HEADERS.indexOf(entry.getKey()), entry.getValue());
    }

    private static void addReviewValidations(Workbook workbook) {
        Sheet normalized = workbook.getSheet(NORMALIZED_SHEET);
        addListValidation(normalized, NORMALIZED_HEADERS.indexOf("记录处置"), List.of("发布", "排除"));
        addListValidation(normalized, NORMALIZED_HEADERS.indexOf("标准处方属性"), List.of("处方药", "非处方药", "其他", "NA"));
        addListValidation(workbook.getSheet(LITERATURE_SHEET), LITERATURE_HEADERS.indexOf("匹配决定"), List.of("新建", "复用已有"));
        addListValidation(workbook.getSheet(LITERATURE_SHEET), LITERATURE_HEADERS.indexOf("审核结论"), List.of("通过", "排除"));
        addListValidation(workbook.getSheet(SITE_SHEET), SITE_HEADERS.indexOf("是否计入统计"), List.of("是", "否"));
        addListValidation(workbook.getSheet(SITE_SHEET), SITE_HEADERS.indexOf("审核结论"), List.of("通过", "排除"));
        addListValidation(workbook.getSheet(METHOD_SHEET), METHOD_HEADERS.indexOf("审核结论"), List.of("通过", "豁免"));
        addListValidation(workbook.getSheet(ICD11_SHEET), ICD11_HEADERS.indexOf("是否进入Sankey"), List.of("是", "否"));
        addListValidation(workbook.getSheet(ICD11_SHEET), ICD11_HEADERS.indexOf("审核结论"), List.of("通过", "排除"));
    }

    private static void addListValidation(Sheet sheet, int column, List<String> values) {
        if (sheet == null || column < 0) return;
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(values.toArray(String[]::new));
        DataValidation validation = helper.createValidation(constraint, new CellRangeAddressList(1, 5000, column, column));
        validation.setShowErrorBox(true);
        validation.setErrorStyle(DataValidation.ErrorStyle.STOP);
        validation.createErrorBox("字段值不符合要求", "请从下拉列表中选择允许值");
        sheet.addValidationData(validation);
    }

    private static void addHeaderComment(Workbook workbook, Sheet sheet, Cell cell, String message) {
        CreationHelper helper = workbook.getCreationHelper();
        var anchor = helper.createClientAnchor();
        anchor.setCol1(cell.getColumnIndex());
        anchor.setCol2(Math.min(cell.getColumnIndex() + 3, SUBMISSION_HEADERS.size()));
        anchor.setRow1(cell.getRowIndex() + 1);
        anchor.setRow2(cell.getRowIndex() + 5);
        Comment comment = sheet.createDrawingPatriarch().createCellComment(anchor);
        comment.setString(helper.createRichTextString(message));
        comment.setAuthor("系统");
        cell.setCellComment(comment);
    }

    private static String fieldHelp(String field) {
        if ("投稿行ID".equals(field)) return "首次投稿留空；系统生成后，退回重传必须保留。";
        if (REQUIRED_SUBMISSION.contains(field)) return "必填字段。空缺会阻止进入审核。";
        if (OPTIONAL_SUBMISSION.contains(field)) return "可选或条件必填字段，具体规则由系统校验。";
        return "按原文填写，不在投稿阶段进行标准化。";
    }

    private static Map<String, String> literatureDraft(String candidateId, Map<String, String> raw) {
        Map<String, String> row = blankRow(LITERATURE_HEADERS);
        put(row, "文献候选ID", candidateId); put(row, "候选文献编号", raw.get("已有文献编号"));
        put(row, "匹配决定", trim(raw.get("已有文献编号")).isBlank() ? "新建" : "复用已有");
        put(row, "已有文献编号", raw.get("已有文献编号")); put(row, "标准DOI", raw.get("DOI"));
        put(row, "文献标题", raw.get("文献标题")); put(row, "发表年份", raw.get("发表年份"));
        put(row, "期刊/来源", raw.get("期刊/来源")); put(row, "keywords", raw.get("keywords"));
        put(row, "abstract", raw.get("abstract")); put(row, "来源文件名或URL", raw.get("来源文件名或URL"));
        put(row, "审核结论", "通过"); return row;
    }

    private static Map<String, String> siteDraft(String groupId, Map<String, String> raw) {
        Map<String, String> row = blankRow(SITE_HEADERS);
        put(row, "点位审核ID", "SITE-" + groupId.substring(3)); put(row, "记录组ID", groupId);
        put(row, "文献编号", raw.get("已有文献编号")); put(row, "报告点位键", "RPS-" + shortHash(groupId + "\u001f" + trim(raw.get("点位名称原文"))));
        copy(row, raw, Map.ofEntries(Map.entry("点位类型原文", "点位类型"), Map.entry("点位名称原文", "点位名称原文"),
                Map.entry("国家原文", "国家原文"), Map.entry("省州原文", "省州原文"), Map.entry("城市原文", "城市原文"),
                Map.entry("处理规模原文", "污水厂处理规模_m3_day"), Map.entry("汇水区人口原文", "汇水区人口")));
        put(row, "标准点位名称", raw.get("点位名称原文")); put(row, "标准国家", raw.get("国家原文"));
        put(row, "标准省州", raw.get("省州原文")); put(row, "标准城市", raw.get("城市原文"));
        put(row, "是否计入统计", "无法拆分".equals(trim(raw.get("点位类型"))) ? "否" : "是");
        put(row, "审核结论", "通过"); return row;
    }

    private static Map<String, String> methodDraft(String candidateId, String methodHash, Map<String, String> raw) {
        Map<String, String> row = blankRow(METHOD_HEADERS);
        put(row, "方法审核ID", "METHOD-" + methodHash); put(row, "文献候选ID", candidateId);
        put(row, "文献编号", raw.get("已有文献编号")); put(row, "原始方法哈希", methodHash);
        put(row, "原始采样方法", raw.get("采样方法原文")); put(row, "原始分析方法", raw.get("分析方法原文"));
        put(row, "标准采样方法", raw.get("采样方法原文")); put(row, "标准分析方法", raw.get("分析方法原文"));
        put(row, "来源文件", raw.get("来源文件名或URL")); put(row, "来源定位", raw.get("页码表号Sheet图号"));
        put(row, "原文证据", raw.get("原文证据")); put(row, "审核结论", "通过"); return row;
    }

    private static Map<String, String> indicationDraft(String indicationId, String candidateId, Map<String, String> raw) {
        Map<String, String> row = blankRow(ICD11_HEADERS);
        put(row, "映射审核ID", "ICD-" + shortHash(indicationId)); put(row, "来源适应症组ID", indicationId);
        put(row, "文献候选ID", candidateId); put(row, "文献编号", raw.get("已有文献编号"));
        put(row, "标准药物名称", raw.get("目标物/药物原文")); put(row, "标准生物标记物名称", raw.get("生物标记物名称原文"));
        put(row, "标准适应症", raw.get("适应症原文")); put(row, "是否进入Sankey", "否");
        put(row, "排除原因", "待审核人员确认ICD-11映射"); put(row, "证据", raw.get("原文证据"));
        put(row, "审核结论", "排除"); return row;
    }

    private static Map<String, String> blankRow(List<String> headers) {
        Map<String, String> row = new LinkedHashMap<>(); headers.forEach(header -> row.put(header, "")); return row;
    }
    private static void put(Map<String, String> row, String key, String value) { row.put(key, trim(value)); }
    private static void copy(Map<String, String> target, Map<String, String> source, Map<String, String> mapping) {
        mapping.forEach((targetKey, sourceKey) -> put(target, targetKey, source.get(sourceKey)));
    }
    private static boolean isBlank(Collection<String> values) { return values.stream().allMatch(value -> trim(value).isBlank()); }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
    private static String normalize(String value) { return trim(value).toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static String normalizeDoi(String value) { return normalize(value).replaceFirst("^https?://(?:dx\\.)?doi\\.org/", "").replaceFirst("^doi\\s*:\\s*", ""); }
    private static boolean parseableNumber(String value) { try { new java.math.BigDecimal(trim(value)); return true; } catch (RuntimeException ignored) { return false; } }

    public record SubmissionRow(int excelRowNumber, String submissionRowId, Map<String, String> values, List<String> errors) {
        public boolean valid() { return errors.isEmpty(); }
    }
    public record ParsedSubmission(List<String> workbookErrors, List<SubmissionRow> rows) {
        public boolean valid() { return workbookErrors.isEmpty() && rows.stream().allMatch(SubmissionRow::valid); }
    }
    public record ReviewRow(String sheetName, int excelRowNumber, Map<String, String> values, List<String> errors) {
        public boolean valid() { return errors.isEmpty(); }
    }
    public record ParsedReview(List<String> workbookErrors, Map<String, List<ReviewRow>> rowsBySheet) {
        public boolean valid() { return workbookErrors.isEmpty() && rowsBySheet.values().stream().flatMap(Collection::stream).allMatch(ReviewRow::valid); }
    }
}
