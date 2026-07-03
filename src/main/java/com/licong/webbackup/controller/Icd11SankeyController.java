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
import java.time.Duration;

@RestController
@RequestMapping("/api/icd11-sankey")
public class Icd11SankeyController {

    private final Icd11SankeyService icd11SankeyService;

    public Icd11SankeyController(Icd11SankeyService icd11SankeyService) {
        this.icd11SankeyService = icd11SankeyService;
    }

    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<Icd11SankeyCategoryResponse>> categories() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .body(ApiResponse.success(icd11SankeyService.getCategories()));
    }

    @GetMapping("/graph")
    public ResponseEntity<ApiResponse<Icd11SankeyGraphResponse>> graph(
            @RequestParam(value = "category", required = false) String category,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        Icd11SankeyGraphResponse graph = icd11SankeyService.getGraph(category);
        String eTag = quoteEtag(DigestUtils.md5DigestAsHex(graph.toString().getBytes(StandardCharsets.UTF_8)));
        if (eTag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(eTag)
                    .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(eTag)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePublic())
                .body(ApiResponse.success(graph));
    }

    private String quoteEtag(String value) {
        return "\"" + value + "\"";
    }
}
