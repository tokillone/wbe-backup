package com.licong.webbackup.dto.methodology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologySamplingMethodResponse {
    private String standard;
    private List<String> samplingClass;
    private List<String> sampleObject;
    private List<String> proportion;
    private List<String> duration;
    private List<String> passiveSampler;
    private List<String> stationStatus;
    private int auditSourceGroups;
    private int impactRows;
}
