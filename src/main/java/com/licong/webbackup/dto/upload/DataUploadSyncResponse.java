package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DataUploadSyncResponse {

    private DataUploadBatchResponse batch;
    private Integer insertedRows;
    private Integer skippedRows;
    private Map<String, Integer> insertedRowsBySheet;
    private List<String> warnings;
}
