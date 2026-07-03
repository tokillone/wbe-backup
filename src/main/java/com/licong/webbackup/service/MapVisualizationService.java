package com.licong.webbackup.service;

import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;

public interface MapVisualizationService {

    MapFilterResponse getFilters();

    MapStatsResponse getStats(String targetClass, String category, String subcategory, String biomarkerKey, String year, String levels);

    MapDetailResponse getDetail(String level, String geoKey, String targetClass, String category,
                                String subcategory, String biomarkerKey, String year);
}
