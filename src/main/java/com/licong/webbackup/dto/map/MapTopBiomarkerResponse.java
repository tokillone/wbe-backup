package com.licong.webbackup.dto.map;

import lombok.Data;

@Data
public class MapTopBiomarkerResponse {

    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
    private String category;
    private String subcategory;
    private Long recordCount;
    private Long doiCount;
    private Long pointCount;
    private Boolean hasPndl;
}
