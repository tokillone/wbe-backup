package com.licong.webbackup.service;

import com.licong.webbackup.dto.HomeOverviewResponse;

public interface HomeService {

    HomeOverviewResponse getOverview(Integer limit, Integer minFrequency, String targetGroup);
}
