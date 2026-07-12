package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapBiomarkerPropertyResponse {

    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
    private String targetClass;
    private String category;
    private String subcategory;
    private Long recordCount;
    private Integer variantCount;
    private List<MapPropertyValueResponse> values;
}
