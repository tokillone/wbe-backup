package com.licong.webbackup.dto.map;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapTrendPointResponse {

    private Integer year;
    private BigDecimal value;
    private Long recordCount;
}
