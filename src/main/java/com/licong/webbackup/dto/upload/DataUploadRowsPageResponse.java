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
    private String rowView;
    private Long reviewPackageId;
    private List<DataUploadRowResponse> rows;
}
