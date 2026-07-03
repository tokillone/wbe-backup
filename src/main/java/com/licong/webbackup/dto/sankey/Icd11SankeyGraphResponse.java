package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class Icd11SankeyGraphResponse {
    private String category;
    private List<Icd11SankeyNodeResponse> nodes;
    private List<Icd11SankeyLinkResponse> links;
    private List<Icd11SankeyPathResponse> paths;
    private Map<String, String> level1Colors;
    private Icd11SankeyStatsResponse stats;
}
