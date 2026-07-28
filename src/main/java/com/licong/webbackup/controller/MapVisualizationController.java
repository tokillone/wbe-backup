package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.map.MapClusterDetailRequest;
import com.licong.webbackup.dto.map.MapDetailResponse;
import com.licong.webbackup.dto.map.MapFilterResponse;
import com.licong.webbackup.dto.map.MapStatsResponse;
import com.licong.webbackup.dto.map.MapReportedSiteResponse;
import com.licong.webbackup.dto.map.MapSiteLinkQcResponse;
import com.licong.webbackup.service.MapVisualizationService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping({"/api/map", "/api/wbe-map"})
public class MapVisualizationController {

    private final MapVisualizationService mapVisualizationService;

    public MapVisualizationController(MapVisualizationService mapVisualizationService) {
        this.mapVisualizationService = mapVisualizationService;
    }

    @GetMapping("/filters")
    public ResponseEntity<ApiResponse<MapFilterResponse>> filters(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return cacheable(mapVisualizationService.getFilters(), ifNoneMatch, Duration.ofHours(6));
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
        return cacheable(
                mapVisualizationService.getStats(targetClass, category, subcategory, biomarkerKey, year, levels),
                ifNoneMatch,
                Duration.ofMinutes(5));
    }

    @GetMapping("/detail")
    public ResponseEntity<ApiResponse<MapDetailResponse>> detail(
            @RequestParam("level") String level,
            @RequestParam("geoKey") String geoKey,
            @RequestParam(value = "targetClass", required = false) String targetClass,
            @RequestParam("category") String category,
            @RequestParam("subcategory") String subcategory,
            @RequestParam("biomarkerKey") String biomarkerKey,
            @RequestParam("year") String year,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return cacheable(
                mapVisualizationService.getDetail(
                        level, geoKey, targetClass, category, subcategory, biomarkerKey, year),
                ifNoneMatch,
                Duration.ofMinutes(5));
    }

    @GetMapping("/regions")
    public ResponseEntity<ApiResponse<MapStatsResponse>> regions(
            @RequestParam(value = "targetClass", required = false) String targetClass,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "subcategory", required = false) String subcategory,
            @RequestParam(value = "biomarkerKey", required = false) String biomarkerKey,
            @RequestParam(value = "year", required = false) String year,
            @RequestParam(value = "levels", required = false) String levels,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return cacheable(
                mapVisualizationService.getStats(
                        targetClass, category, subcategory, biomarkerKey, year, levels),
                ifNoneMatch,
                Duration.ofMinutes(5));
    }

    @GetMapping("/regions/{regionKey}/sites")
    public ResponseEntity<ApiResponse<List<MapReportedSiteResponse>>> regionSites(
            @PathVariable("regionKey") String regionKey,
            @RequestParam("level") String level,
            @RequestParam(value = "targetClass", required = false) String targetClass,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "subcategory", required = false) String subcategory,
            @RequestParam(value = "biomarkerKey", required = false) String biomarkerKey,
            @RequestParam(value = "year", required = false) String year,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return cacheable(
                mapVisualizationService.getRegionSites(
                        level, regionKey, targetClass, category, subcategory, biomarkerKey, year),
                ifNoneMatch,
                Duration.ofMinutes(5));
    }

    @GetMapping("/site-link-qc")
    public ResponseEntity<ApiResponse<MapSiteLinkQcResponse>> siteLinkQc(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return cacheable(mapVisualizationService.getSiteLinkQc(), ifNoneMatch, Duration.ofMinutes(1));
    }

    @PostMapping("/cluster-detail")
    public ResponseEntity<ApiResponse<MapDetailResponse>> clusterDetail(
            @RequestBody MapClusterDetailRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(mapVisualizationService.getClusterDetail(request)));
    }

    private <T> ResponseEntity<ApiResponse<T>> cacheable(T data,
                                                         String ifNoneMatch,
                                                         Duration maxAge) {
        ApiResponse<T> body = ApiResponse.success(data);
        String eTag = quoteEtag(DigestUtils.md5DigestAsHex(
                body.toString().getBytes(StandardCharsets.UTF_8)));
        CacheControl cacheControl = CacheControl.maxAge(maxAge).cachePublic().mustRevalidate();
        if (eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(eTag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(cacheControl)
                .body(body);
    }

    private String quoteEtag(String value) {
        return "\"" + value + "\"";
    }
}
