package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DataUploadRowsPageResponse {

    private Long uploadId;
    private Integer page;
    private Integer size;
    private Long total;
    private List<DataUploadRowResponse> rows;
}
