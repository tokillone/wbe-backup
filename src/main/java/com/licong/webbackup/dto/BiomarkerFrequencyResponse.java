package com.licong.webbackup.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class BiomarkerFrequencyResponse {

    private String name;
    private Long frequency;
    private String category;
    private String targetCategory;
    private String targetGroup;
    private Long docs;
    private Long rows;
    private List<BiomarkerSubclassResponse> subclassOptions = new ArrayList<>();
    private List<BiomarkerTrendPointResponse> trend = new ArrayList<>();
}
