package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class Icd11SankeyPathResponse {
    private String pathId;
    private String level1;
    private String level2;
    private String level3;
    private String mappingLevel;
    private String drug;
    private String biomarker;
    private List<String> biomarkerAliases;
    private BigDecimal weight;
    private BigDecimal share;
    private List<String> nodeIds;
}
