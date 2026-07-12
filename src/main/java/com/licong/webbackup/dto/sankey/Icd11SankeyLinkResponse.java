package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class Icd11SankeyLinkResponse {
    private String linkId;
    private String source;
    private String target;
    private BigDecimal value;
    private String level1;
    private String sourceLabel;
    private String targetLabel;
    private String edgeType;
    private String mappingLevel;
    private List<String> pathIds;
    private String color;
}
