package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DataUploadPreviewResponse {

    private DataUploadBatchResponse batch;
    private List<String> requiredHeaders;
    private List<String> optionalHeaders;
    private List<String> headerErrors;
    private List<String> batchWarnings;
    private List<DataUploadRowResponse> previewRows;
    private List<DataUploadSheetSummaryResponse> sheetSummaries;
    private Map<String, List<DataUploadRowResponse>> previewRowsBySheet;
}
