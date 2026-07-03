package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class Icd11SankeyTopItemResponse {
    private String name;
    private BigDecimal value;
    private BigDecimal share;
}
