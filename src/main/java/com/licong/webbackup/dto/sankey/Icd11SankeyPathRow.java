package com.licong.webbackup.dto.sankey;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Icd11SankeyPathRow {
    private Long sankeyPathId;
    private String targetCategory;
    private String substanceCategory;
    private String substanceSubclass;
    private String drugName;
    private String indicationOriginal;
    private String biomarkerName;
    private String biomarkerAlias;
    private String normalizedIndication;
    private String diseaseEntity;
    private String icd11Level1Code;
    private String icd11Level1Name;
    private String icd11Level2Code;
    private String icd11Level2Name;
    private String icd11Level3Code;
    private String icd11Level3Name;
    private String mappingLevel;
    private String matchType;
    private String reviewStatus;
    private String note;
    private String biomarkerCas;
    private BigDecimal literatureCount;
    private Long dataRowCount;
}
