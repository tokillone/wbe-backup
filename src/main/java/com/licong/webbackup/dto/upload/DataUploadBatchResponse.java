package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DataUploadBatchResponse {

    private Long uploadId;
    private String fileName;
    private String status;
    private Long uploadedBy;
    private String uploadedByName;
    private String uploadedByRole;
    private Integer totalRows;
    private Integer validRows;
    private Integer errorRows;
    private Integer warningRows;
    private Integer syncedRows;
    private String duplicateMessage;
    private LocalDateTime createdAt;
    private LocalDateTime syncedAt;
    private Long reviewedBy;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    private String reviewAction;
    private String reviewNote;
    private Long syncedBy;
    private String syncedByName;
    private String syncErrorMessage;
    private Long sourceReviewedBy;
    private String sourceReviewedByName;
    private LocalDateTime sourceReviewedAt;
    private String sourceReviewNote;
    private Long currentPackageId;
    private Integer currentPackageVersion;
    private String currentPackageFileName;
    private String currentPackageStatus;
    private Integer currentPackageRows;
    private Long approvedPackageId;
    private Boolean reviewChecklistComplete;
    private Integer currentRevisionNo;
    private Long publishedReleaseId;
}
