package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MapBreakdownItemResponse {

    private String label;
    private Long recordCount;
    private BigDecimal percentage;
}
