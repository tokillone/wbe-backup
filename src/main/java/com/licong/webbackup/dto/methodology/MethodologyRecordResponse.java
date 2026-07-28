package com.licong.webbackup.dto.methodology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologyRecordResponse {
    private String doc;
    private String doi;
    private String targetClass;
    private String category;
    private String subcategory;
    private String drug;
    private String marker;
    private String prescription;
    private String samplingRaw;
    private String samplingStandard;
    private String samplingDetail;
    private String samplingClass;
    private String sampleObject;
    private String proportion;
    private String duration;
    private String passiveSampler;
    private String stationStatus;
    private String analysisRaw;
    private String analysisGroup;
    private String country;
}
