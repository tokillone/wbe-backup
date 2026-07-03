package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapDiagnosticsResponse {

    private long statsRowCount;
    private long positivePndlCount;
    private long convertiblePndlCount;
    private long mappablePndlCount;
    private long geoLocationCount;
    private String message;
}
