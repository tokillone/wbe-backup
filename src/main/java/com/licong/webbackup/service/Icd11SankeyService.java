package com.licong.webbackup.service;

import com.licong.webbackup.dto.sankey.Icd11SankeyCategoryResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;

public interface Icd11SankeyService {

    Icd11SankeyCategoryResponse getCategories();

    Icd11SankeyGraphResponse getGraph(String category);

    /**
     * Clears public read-model caches after a successful workbook synchronization.
     */
    void invalidateCache();

    /**
     * Changes whenever cached ICD11 data is invalidated and is used for HTTP validators.
     */
    long cacheRevision();
}
