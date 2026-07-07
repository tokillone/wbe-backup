package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.map.MapClusterDetailRequest;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.service.MapVisualizationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequestMapping("/api/map")
public class MapVisualizationController {

    private final MapVisualizationService mapVisualizationService;

    public MapVisualizationController(MapVisualizationService mapVisualizationService) {
        this.mapVisualizationService = mapVisualizationService;
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<MapFilterResponse>> filters() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(ApiResponse.success(mapVisualizationService.getFilters()));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<MapStatsResponse>> stats(
            @RequestParam(value = "targetClass", required = false) String targetClass,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "subcategory", required = false) String subcategory,
            @RequestParam(value = "biomarkerKey", required = false) String biomarkerKey,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        MapStatsResponse stats = mapVisualizationService.getStats(targetClass, category, subcategory, biomarkerKey, year, levels);
        String eTag = quoteEtag(DigestUtils.md5DigestAsHex(stats.toString().getBytes(StandardCharsets.UTF_8)));
        if (eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(eTag)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(ApiResponse.success(stats));
    }

    @GetMapping("/detail")
    public ApiResponse<MapDetailResponse> detail(
            @RequestParam("level") String level,
            @RequestParam("geoKey") String geoKey,
            @RequestParam(value = "targetClass", required = false) String targetClass,
            @RequestParam("category") String category,
            @RequestParam("subcategory") String subcategory,
            @RequestParam("biomarkerKey") String biomarkerKey,
            @RequestParam("year") String year) {
        return ApiResponse.success(mapVisualizationService.getDetail(level, geoKey, targetClass, category, subcategory, biomarkerKey, year));
    }

    @PostMapping("/cluster-detail")
    public ApiResponse<MapDetailResponse> clusterDetail(@RequestBody MapClusterDetailRequest request) {
        return ApiResponse.success(mapVisualizationService.getClusterDetail(request));
    }

    private String quoteEtag(String value) {
        return "\"" + value + "\"";
    }
}
