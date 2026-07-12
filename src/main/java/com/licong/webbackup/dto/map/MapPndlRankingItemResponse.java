package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class MapPndlRankingItemResponse {

    private Integer rank;
    private String level;
    private String geoKey;
    private String displayName;
    private BigDecimal pndlMedianMgD1000inh;
    private BigDecimal pndlGeomeanMgD1000inh;
    private Long recordCount;
    private Long doiCount;
    private Long pointCount;
    private Long yearCount;
    private Long pndlRecordCount;
    private Long pndlDoiCount;
    private Long pndlPointCount;
    private Long pndlYearCount;
    private String pndlSources;
    private Boolean selected;
}
