package com.licong.webbackup.dto.upload;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class DataUploadBatchPageResponse {

    private List<DataUploadBatchResponse> items;
    private Integer page;
    private Integer size;
    private Long total;
    private Integer totalPages;
}
