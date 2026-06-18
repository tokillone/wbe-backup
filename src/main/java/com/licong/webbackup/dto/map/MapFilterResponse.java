package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class MapFilterResponse {

    private List<String> categories;
    private Map<String, List<String>> subcategoriesByCategory;
    private Map<String, List<MapBiomarkerOptionResponse>> biomarkersByCategorySubcategory;
    private Map<String, List<String>> yearsBySelection;
    private MapFilterSelectionResponse defaultSelection;
}
