package com.licong.webbackup.dto.methodology;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MethodologyOptionsResponse {
    private List<MethodologySamplingMethodResponse> samplingMethods;
    private Map<String, List<String>> options;
}
