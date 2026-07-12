package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapPropertyValueResponse {

    private String text;
    private Long count;
}
