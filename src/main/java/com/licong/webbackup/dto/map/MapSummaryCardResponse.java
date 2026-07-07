package com.licong.webbackup.dto.map;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MapSummaryCardResponse {

    private String label;
    private String value;
    private String note;
}
