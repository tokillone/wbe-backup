package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyCategoryResponse;
import com.licong.webbackup.dto.sankey.Icd11SankeyGraphResponse;
import com.licong.webbackup.service.Icd11SankeyService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/icd11-sankey")
public class Icd11SankeyController {

    private final Icd11SankeyService icd11SankeyService;

    public Icd11SankeyController(Icd11SankeyService icd11SankeyService) {
        this.icd11SankeyService = icd11SankeyService;
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<Icd11SankeyCategoryResponse>> categories(
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Icd11SankeyCategoryResponse categories = icd11SankeyService.getCategories();
        String eTag = responseEtag("categories");
        if (etagMatches(ifNoneMatch, eTag)) {
            return notModified(eTag);
        }
        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(publicRevalidatingCache())
                .body(ApiResponse.success(categories));
    }

    @GetMapping({"/graph", "/graph-v2"})
    public ResponseEntity<ApiResponse<Icd11SankeyGraphResponse>> graph(
            @RequestParam(value = "category", required = false) String category,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Icd11SankeyGraphResponse graph = icd11SankeyService.getGraph(category);
        String eTag = responseEtag("graph:" + graph.getCategory());
        if (etagMatches(ifNoneMatch, eTag)) {
            return notModified(eTag);
        }
        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(publicRevalidatingCache())
                .body(ApiResponse.success(graph));
    }

    private String responseEtag(String resource) {
        String value = icd11SankeyService.cacheRevision() + ":" + resource;
        return "\"" + DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8)) + "\"";
    }

    private boolean etagMatches(String ifNoneMatch, String eTag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalized = candidate.trim();
            if ("*".equals(normalized) || eTag.equals(normalized)
                    || (normalized.startsWith("W/") && eTag.equals(normalized.substring(2)))) {
                return true;
            }
        }
        return false;
    }

    private CacheControl publicRevalidatingCache() {
        return CacheControl.noCache().cachePublic().mustRevalidate();
    }

    private <T> ResponseEntity<ApiResponse<T>> notModified(String eTag) {
        return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                .eTag(eTag)
                .cacheControl(publicRevalidatingCache())
                .build();
    }
}
