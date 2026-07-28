package com.licong.webbackup.dto.methodology;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MethodologySamplingMethodRow {
    private String standard;
    private String samplingClassJson;
    private String sampleObjectJson;
    private String proportionJson;
    private String durationJson;
    private String passiveSamplerJson;
    private String stationStatusJson;
    private int auditSourceGroups;
    private int impactRows;
}
