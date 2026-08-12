package com.licong.webbackup.service;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SimplifiedUploadWorkbookTest {

    @TempDir
    Path tempDir;

    @Test
    void createsExactlyOneSubmissionSheetWithStableContractAndValidations() throws Exception {
        byte[] bytes = SimplifiedUploadWorkbook.createSubmissionTemplate();
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
            Sheet sheet = workbook.getSheet(SimplifiedUploadWorkbook.SUBMISSION_SHEET);
            assertThat(sheet).isNotNull();
            assertThat((int) sheet.getRow(0).getLastCellNum()).isEqualTo(SimplifiedUploadWorkbook.SUBMISSION_HEADERS.size());
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("投稿行ID");
            assertThat(sheet.getDataValidations()).isNotEmpty();
            assertThat(sheet.getPaneInformation()).isNotNull();
        }
    }

    @Test
    void acceptsDoiFallbackAndGeneratesImmutableSubmissionRowId() throws Exception {
        Path file = writeSubmissionWorkbook(Map.ofEntries(
                Map.entry("投稿类型", "新文献"), Map.entry("DOI", ""),
                Map.entry("文献标题", "A wastewater study"), Map.entry("发表年份", "2025"),
                Map.entry("期刊/来源", "Water Research"), Map.entry("来源文件名或URL", "paper.pdf"),
                Map.entry("来源记录编号", "R-001"), Map.entry("生物标记物名称原文", "Marker A"),
                Map.entry("采样方法原文", "24 h composite"), Map.entry("分析方法原文", "LC-MS/MS"),
                Map.entry("点位类型", "污水厂"), Map.entry("点位名称原文", "Plant A"),
                Map.entry("国家原文", "China"), Map.entry("样品采集时间原文", "2025-01"),
                Map.entry("指标类型", "进水浓度"), Map.entry("统计量", "average"),
                Map.entry("原始数值", "12.3"), Map.entry("原始单位", "ng/L"),
                Map.entry("数值来源", "文献直接报告"), Map.entry("页码表号Sheet图号", "Table 2"),
                Map.entry("原文证据", "Mean concentration 12.3 ng/L")
        ));
        var parsed = SimplifiedUploadWorkbook.parseSubmission(file, null);
        assertThat(parsed.valid()).isTrue();
        assertThat(parsed.rows()).hasSize(1);
        assertThat(parsed.rows().getFirst().submissionRowId()).startsWith("SR-");
        assertThat(parsed.rows().getFirst().values().get("投稿行ID"))
                .isEqualTo(parsed.rows().getFirst().submissionRowId());
    }

    @Test
    void rejectsForeignRowIdAndIncompleteCalculatedValueEvidence() throws Exception {
        Path file = writeSubmissionWorkbook(Map.ofEntries(
                Map.entry("投稿行ID", "SR-foreign"), Map.entry("投稿类型", "补充已有文献"),
                Map.entry("已有文献编号", "WBE0001"), Map.entry("文献标题", "Study"),
                Map.entry("发表年份", "2024"), Map.entry("期刊/来源", "Journal"),
                Map.entry("来源文件名或URL", "source.xlsx"), Map.entry("来源记录编号", "R1"),
                Map.entry("生物标记物名称原文", "Marker"), Map.entry("采样方法原文", "grab"),
                Map.entry("分析方法原文", "NA"), Map.entry("点位类型", "无法拆分"),
                Map.entry("点位名称原文", "NA"), Map.entry("国家原文", "NA"),
                Map.entry("样品采集时间原文", "2024"), Map.entry("指标类型", "校准系数"),
                Map.entry("统计量", "直接值"), Map.entry("原始数值", "0.5"),
                Map.entry("原始单位", "1"), Map.entry("数值来源", "投稿人计算"),
                Map.entry("页码表号Sheet图号", "Sheet1"), Map.entry("原文证据", "calculated")
        ));
        var parsed = SimplifiedUploadWorkbook.parseSubmission(file, Set.of("SR-known"));
        assertThat(parsed.valid()).isFalse();
        assertThat(parsed.rows().getFirst().errors())
                .anyMatch(error -> error.contains("投稿行ID不属于该批次"))
                .anyMatch(error -> error.contains("计算换算说明"));
    }

    @Test
    void createsReviewDraftWithExactlyFiveSheetsAndNoCoreMarkerInput() throws Exception {
        Map<String, String> values = SimplifiedUploadWorkbook.SUBMISSION_HEADERS.stream()
                .collect(java.util.stream.Collectors.toMap(header -> header, header -> "", (a, b) -> a, java.util.LinkedHashMap::new));
        values.put("投稿行ID", "SR-1");
        values.put("投稿类型", "新文献");
        values.put("DOI", "10.1000/test");
        values.put("文献标题", "Study");
        values.put("发表年份", "2025");
        values.put("期刊/来源", "Journal");
        values.put("来源文件名或URL", "paper.pdf");
        values.put("来源记录编号", "R1");
        values.put("生物标记物名称原文", "Marker");
        values.put("采样方法原文", "24h composite");
        values.put("分析方法原文", "LC-MS/MS");
        values.put("点位类型", "污水厂");
        values.put("点位名称原文", "Plant");
        values.put("国家原文", "China");
        values.put("样品采集时间原文", "2025-01");
        values.put("指标类型", "进水浓度");
        values.put("统计量", "average");
        values.put("原始数值", "1.2");
        values.put("原始单位", "ng/L");
        values.put("数值来源", "文献直接报告");
        values.put("页码表号Sheet图号", "Table 1");
        values.put("原文证据", "1.2 ng/L");
        byte[] draft = SimplifiedUploadWorkbook.createReviewDraft(List.of(
                new SimplifiedUploadWorkbook.SubmissionRow(2, "SR-1", values, List.of())));
        try (Workbook workbook = new XSSFWorkbook(new java.io.ByteArrayInputStream(draft))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(5);
            assertThat(java.util.stream.IntStream.range(0, workbook.getNumberOfSheets())
                    .mapToObj(workbook::getSheetName).toList()).containsExactlyElementsOf(SimplifiedUploadWorkbook.REVIEW_SHEETS);
            assertThat(workbook.getSheet("核心标记物优先级识别")).isNull();
            assertThat(workbook.getSheet(SimplifiedUploadWorkbook.NORMALIZED_SHEET).getRow(1).getCell(1).getStringCellValue())
                    .isEqualTo("SR-1");
        }
    }

    private Path writeSubmissionWorkbook(Map<String, String> values) throws Exception {
        Path file = tempDir.resolve("submission-" + System.nanoTime() + ".xlsx");
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(SimplifiedUploadWorkbook.SUBMISSION_SHEET);
            Row header = sheet.createRow(0);
            Row row = sheet.createRow(1);
            for (int i = 0; i < SimplifiedUploadWorkbook.SUBMISSION_HEADERS.size(); i++) {
                String field = SimplifiedUploadWorkbook.SUBMISSION_HEADERS.get(i);
                header.createCell(i, CellType.STRING).setCellValue(field);
                row.createCell(i, CellType.STRING).setCellValue(values.getOrDefault(field, ""));
            }
            try (var output = Files.newOutputStream(file)) { workbook.write(output); }
        }
        return file;
    }
}
