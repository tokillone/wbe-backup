package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class DataUploadRowResponse {

    private Long rowId;
    private Integer excelRowNumber;
    private String status;
    private List<String> errors;
    private List<String> warnings;
    private Long syncedMeasurementId;
    private Map<String, String> data;
}
