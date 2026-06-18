package com.licong.webbackup.dto.map;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapSourceRecordResponse {

    private Long measurementId;
    private String drugName;
    private String biomarkerName;
    private String biomarkerCas;
    private String doi;
    private String country;
    private String province;
    private String city;
    private String plantName;
    private String samplePeriod;
    private String sourceWorkbook;
    private Integer originalRowNumber;
    private BigDecimal pndlMgD1000inh;
    private String pndlSource;
}
