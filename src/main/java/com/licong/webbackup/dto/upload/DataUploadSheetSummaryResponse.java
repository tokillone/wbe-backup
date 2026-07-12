package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DataUploadSheetSummaryResponse {

    private String sheetName;
    private Integer totalRows;
    private Integer validRows;
    private Integer warningRows;
    private Integer errorRows;
}
