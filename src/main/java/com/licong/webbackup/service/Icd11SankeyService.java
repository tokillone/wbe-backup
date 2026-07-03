package com.licong.webbackup.service;

import com.licong.webbackup.dto.sankey.Icd11SankeyCategoryResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;

public interface Icd11SankeyService {

    Icd11SankeyCategoryResponse getCategories();

    Icd11SankeyGraphResponse getGraph(String category);
}
