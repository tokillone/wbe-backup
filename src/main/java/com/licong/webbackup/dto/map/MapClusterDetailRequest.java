package com.licong.webbackup.dto.map;

import lombok.Data;

import java.util.List;

@Data
public class MapClusterDetailRequest {

    private String targetClass;
    private String category;
    private String subcategory;
    private String biomarkerKey;
    private String year;
    private Integer limit;
    private List<MapClusterLocationRequest> locations;
}
