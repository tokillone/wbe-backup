package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.dto.methodology.MethodologyDataResponse;
import com.licong.webbackup.dto.methodology.MethodologyOptionsResponse;
import com.licong.webbackup.dto.methodology.MethodologyRecordResponse;
import com.licong.webbackup.service.MethodologyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/methodology")
public class MethodologyController {

    private static final CacheControl PUBLIC_CACHE = CacheControl.maxAge(Duration.ofHours(1))
            .cachePublic()
            .mustRevalidate();

    private final MethodologyService methodologyService;

    public MethodologyController(MethodologyService methodologyService) {
        this.methodologyService = methodologyService;
    }

    @GetMapping("/overview")
    public ResponseEntity<ApiResponse<Map<String, Object>>> overview(WebRequest request) {
        return cacheable(request, "overview", methodologyService::getOverview);
    }

    @GetMapping("/options")
    public ResponseEntity<ApiResponse<MethodologyOptionsResponse>> options(WebRequest request) {
        return cacheable(request, "options", methodologyService::getOptions);
    }

    @GetMapping("/records")
    public ResponseEntity<ApiResponse<List<MethodologyRecordResponse>>> records(WebRequest request) {
        return cacheable(request, "records", methodologyService::getRecords);
    }

    @GetMapping("/data")
    public ResponseEntity<ApiResponse<MethodologyDataResponse>> data(WebRequest request) {
        return cacheable(request, "data", methodologyService::getData);
    }

    private <T> ResponseEntity<ApiResponse<T>> cacheable(WebRequest request,
                                                         String representation,
                                                         Supplier<T> supplier) {
        String etag = "\"" + methodologyService.getVersion() + "-" + representation + "\"";
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(PUBLIC_CACHE)
                    .eTag(etag)
                    .build();
        }
        return ResponseEntity.ok()
                .cacheControl(PUBLIC_CACHE)
                .eTag(etag)
                .body(ApiResponse.success(supplier.get()));
    }
}
