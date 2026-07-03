package com.licong.webbackup.dto.sankey;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class Icd11SankeyCategoryResponse {
    private List<String> categories;
    private String defaultCategory;
}
