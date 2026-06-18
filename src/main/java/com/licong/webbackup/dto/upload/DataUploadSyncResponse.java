package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DataUploadSyncResponse {

    private DataUploadBatchResponse batch;
    private Integer insertedRows;
    private Integer skippedRows;
    private List<String> warnings;
}
