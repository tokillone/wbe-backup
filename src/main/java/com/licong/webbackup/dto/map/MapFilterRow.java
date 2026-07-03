package com.licong.webbackup.dto.map;

import lombok.Data;

@Data
public class MapFilterRow {

    private String targetClass;
    private String category;
    private String subcategory;
    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
    private String yearLabel;
}
