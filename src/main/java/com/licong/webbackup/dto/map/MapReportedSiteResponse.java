package com.licong.webbackup.dto.map;

import lombok.Data;

@Data
public class MapReportedSiteResponse {

    private String reportedSiteKey;
    private String effectiveSiteKey;
    private String literatureCode;
    private String doi;
    private String country;
    private String province;
    private String city;
    private String rawPlantName;
    private String canonicalPlantName;
    private String confirmedSiteId;
    private String siteNote;
    private String matchStatus;
    private Long recordCount;
}
