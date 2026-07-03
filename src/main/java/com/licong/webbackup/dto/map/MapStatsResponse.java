package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapStatsResponse {

    private MapLegendResponse legend;
    private MapSummaryResponse summary;
    private List<MapRegionStatResponse> regions;
    private List<MapRegionStatResponse> points;
    private MapDiagnosticsResponse diagnostics;
}
