package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.HomeOverviewResponse;
import com.licong.webbackup.service.HomeService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/home")
public class HomeController {

    private final HomeService homeService;

    public HomeController(HomeService homeService) {
        this.homeService = homeService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<HomeOverviewResponse>> overview(
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "minFrequency", required = false) Integer minFrequency,
            @RequestParam(value = "targetGroup", required = false) String targetGroup,
            @RequestParam(value = "targetCategory", required = false) String targetCategory) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5))
                        .cachePublic()
                        .staleWhileRevalidate(Duration.ofMinutes(10)))
                .body(ApiResponse.success(
                        homeService.getOverview(limit, minFrequency, targetGroup, targetCategory)));
    }
}
