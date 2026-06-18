package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MapDetailResponse {

    private MapRegionStatResponse region;
    private List<MapSourceRecordResponse> sources;
}
