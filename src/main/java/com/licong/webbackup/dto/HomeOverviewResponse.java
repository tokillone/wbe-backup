package com.licong.webbackup.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class HomeOverviewResponse {

    private List<BiomarkerFrequencyResponse> biomarkerFrequencies;
}
