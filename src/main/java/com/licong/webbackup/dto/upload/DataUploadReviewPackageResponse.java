package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class DataUploadReviewPackageResponse {

    private Long packageId;
    private Long uploadId;
    private Integer versionNo;
    private String fileName;
    private String status;
    private Long uploadedBy;
    private String uploadedByName;
    private Integer totalRows;
    private Integer validRows;
    private Integer errorRows;
    private Integer warningRows;
    private LocalDateTime createdAt;
    private List<String> validationErrors;
    private Map<String, Object> diffSummary;
    private List<DataUploadSheetSummaryResponse> sheetSummaries;
}
