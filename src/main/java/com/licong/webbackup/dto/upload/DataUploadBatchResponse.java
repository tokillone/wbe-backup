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
    private Integer totalRows;
    private Integer validRows;
    private Integer errorRows;
    private Integer warningRows;
    private Integer syncedRows;
    private String duplicateMessage;
    private LocalDateTime createdAt;
    private LocalDateTime syncedAt;
}
