package com.licong.webbackup.dto.map;

import lombok.Data;

@Data
public class MapBiomarkerPropertyRow {

    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
    private String targetClass;
    private String category;
    private String subcategory;
    private String propertyText;
    private Long recordCount;
}
