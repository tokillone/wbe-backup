package com.licong.webbackup.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.coremarker.CoreMarkerPriorityOverviewResponse;
import com.licong.webbackup.service.CoreMarkerPriorityService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/core-marker-priority")
public class CoreMarkerPriorityController {

    private static final CacheControl DATABASE_DATA_CACHE = CacheControl.noStore();

    private final CoreMarkerPriorityService coreMarkerPriorityService;

    public CoreMarkerPriorityController(CoreMarkerPriorityService coreMarkerPriorityService) {
        this.coreMarkerPriorityService = coreMarkerPriorityService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<CoreMarkerPriorityOverviewResponse>> overview(WebRequest request) {
        CoreMarkerPriorityOverviewResponse overview = coreMarkerPriorityService.getOverview();
        String etag = weakEtag("overview", overview.hashCode());
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(DATABASE_DATA_CACHE)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(DATABASE_DATA_CACHE)
                .eTag(etag)
                .body(ApiResponse.success(overview));
    }

    @GetMapping("/details/{markerId}")
    public ResponseEntity<ApiResponse<JsonNode>> detail(@PathVariable String markerId, WebRequest request) {
        JsonNode detail = coreMarkerPriorityService.getDetail(markerId);
        String etag = weakEtag("detail-" + markerId, detail.hashCode());
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(DATABASE_DATA_CACHE)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(DATABASE_DATA_CACHE)
                .eTag(etag)
                .body(ApiResponse.success(detail));
    }

    private String weakEtag(String resource, int contentHash) {
        return "W/\"" + resource + "-" + Integer.toUnsignedString(contentHash, 16) + "\"";
    }
}
