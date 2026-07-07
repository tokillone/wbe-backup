package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapPndlComparisonResponse {

    private String key;
    private String label;
    private String scopeLevel;
    private String unit;
    private String note;
    private String selectedRegionId;
    private Boolean highlightSelected;
    private List<MapPndlRankingItemResponse> rows;
}
