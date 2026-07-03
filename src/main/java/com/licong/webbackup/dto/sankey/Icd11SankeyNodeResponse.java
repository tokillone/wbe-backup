package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class Icd11SankeyNodeResponse {
    private String name;
    private String displayName;
    private String kind;
    private Integer depth;
    private BigDecimal value;
    private String searchText;
    private String level1;
    private String color;
}
