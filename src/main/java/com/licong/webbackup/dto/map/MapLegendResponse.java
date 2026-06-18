package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
public class MapLegendResponse {

    private BigDecimal min;
    private BigDecimal max;
    private String unit;
    private List<String> colors;
}
