package com.licong.webbackup.service;

import com.licong.webbackup.dto.map.MapClusterDetailRequest;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.dto.map.MapReportedSiteResponse;
import com.licong.webbackup.dto.map.MapSiteLinkQcResponse;

import java.util.List;

public interface MapVisualizationService {

    MapFilterResponse getFilters();

    MapStatsResponse getStats(String targetClass, String category, String subcategory, String biomarkerKey, String year, String levels);

    MapDetailResponse getDetail(String level, String geoKey, String targetClass, String category,
                                String subcategory, String biomarkerKey, String year);

    MapDetailResponse getClusterDetail(MapClusterDetailRequest request);

    List<MapReportedSiteResponse> getRegionSites(String level, String geoKey, String targetClass,
                                                 String category, String subcategory, String biomarkerKey,
                                                 String year);

    MapSiteLinkQcResponse getSiteLinkQc();
}
