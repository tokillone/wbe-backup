package com.licong.webbackup.dto.map;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapRegionStatResponse {

    private String level;
    private String geoKey;
    private String parentGeoKey;
    private String displayName;
    private String country;
    private String province;
    private String city;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String category;
    private String subcategory;
    private String biomarkerKey;
    private String biomarkerLabel;
    private String biomarkerCas;
    private String yearLabel;
    private BigDecimal pndlMedianMgD1000inh;
    private BigDecimal pndlGeomeanMgD1000inh;
    private BigDecimal pndlMeanMgD1000inh;
    private BigDecimal pndlMinMgD1000inh;
    private BigDecimal pndlMaxMgD1000inh;
    private Long recordCount;
    private Long doiCount;
    private Long yearCount;
    private Long cityCount;
    private Long pointCount;
    private Long biomarkerCount;
    private Long pndlRecordCount;
    private Long pndlDoiCount;
    private Long pndlPointCount;
    private Long pndlYearCount;
    private String pndlSources;
    private String pointCountBasis;
    private Boolean crossDocumentMergeEnabled;
    private String pointGeometryBasis;
}
