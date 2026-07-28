package com.licong.webbackup.dto.coremarker;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public record CoreMarkerPriorityOverviewResponse(
        List<JsonNode> rows,
        JsonNode summary
) {
}
