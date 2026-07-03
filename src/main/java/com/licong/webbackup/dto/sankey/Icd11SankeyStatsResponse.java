package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class Icd11SankeyStatsResponse {
    private BigDecimal totalWeight;
    private Integer level1;
    private Integer level2;
    private Integer drug;
    private Integer biomarker;
    private Integer relations;
    private Integer maxNodes;
    private List<Icd11SankeyTopItemResponse> topLevel1;
    private List<Icd11SankeyTopItemResponse> topDrug;
    private List<Icd11SankeyTopItemResponse> topBiomarker;
}
