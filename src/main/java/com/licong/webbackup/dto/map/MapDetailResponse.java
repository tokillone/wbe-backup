package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapDetailResponse {

    private String title;
    private String subtitle;
    private Boolean cluster;
    private MapRegionStatResponse region;
    private List<MapRegionStatResponse> locations;
    private List<MapSummaryCardResponse> summaryCards;
    private List<MapTopBiomarkerResponse> topBiomarkers;
    private List<MapPndlRankingItemResponse> pndlRanking;
    private List<MapPndlComparisonResponse> pndlComparisons;
    private List<MapTrendSeriesResponse> trendSeries;
    private List<MapBiomarkerPropertyResponse> biomarkerProperties;
    private List<MapBreakdownItemResponse> categoryBreakdown;
    private List<MapSourceRecordResponse> sources;
    private List<MapSourceRecordResponse> sourceRecords;
}
