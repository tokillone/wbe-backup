package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapSummaryResponse {

    private long countryCount;
    private long admin1Count;
    private long cityCount;
    private long pointCount;
    private long recordCount;
    private long doiCount;
}
