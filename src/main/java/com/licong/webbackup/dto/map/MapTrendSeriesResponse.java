package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapTrendSeriesResponse {

    private String metricKey;
    private String label;
    private String unit;
    private List<MapTrendPointResponse> points;
}
