package com.licong.webbackup.dto.map;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class MapSiteLinkQcResponse {

    private Long uploadId;
    private Boolean mergeConfirmedCrossDocumentSites;
    private String pointCountBasis;
    private Long siteRows;
    private Long includedSites;
    private Long excludedSites;
    private Long mappedSites;
    private Long unmappedSites;
    private Long recordRows;
    private Long exactRecords;
    private Long multiSiteRecords;
    private Long locationFallbackRecords;
    private Long excludedRecords;
    private Long unmatchedCountryRecords;
    private Long unmatchedRecords;
    private Map<String, Long> matchStatusCounts;
    private List<MapReportedSiteResponse> unmappedSiteRows;
    private LocalDateTime createdAt;
}
