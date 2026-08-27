package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapBiomarkerPathResponse {

    private String targetClass;
    private String category;
    private String subcategory;
    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
}
