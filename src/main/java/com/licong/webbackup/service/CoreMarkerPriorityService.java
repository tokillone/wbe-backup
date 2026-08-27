package com.licong.webbackup.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;

public interface CoreMarkerPriorityService {

    CoreMarkerPriorityOverviewResponse getOverview();

    JsonNode getDetail(String markerId);
}
