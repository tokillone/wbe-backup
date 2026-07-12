package com.licong.webbackup.dto.map;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MapMetricObservationRow {

    private String metricKey;
    private String metricLabel;
    private String unit;
    private Integer year;
    private BigDecimal value;
}
