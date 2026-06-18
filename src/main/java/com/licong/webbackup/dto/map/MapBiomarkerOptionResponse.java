package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapBiomarkerOptionResponse {

    private String key;
    private String label;
    private String cas;
}
