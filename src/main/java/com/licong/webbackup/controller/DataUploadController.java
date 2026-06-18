package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.common.SecuritySupport;
import com.licong.webbackup.dto.upload.DataUploadBatchResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadRowsPageResponse;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.service.DataUploadService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

@RestController
@RequestMapping("/api/data-uploads")
public class DataUploadController {

    private final SecuritySupport securitySupport;
    private final DataUploadService dataUploadService;

    public DataUploadController(SecuritySupport securitySupport, DataUploadService dataUploadService) {
        this.securitySupport = securitySupport;
        this.dataUploadService = dataUploadService;
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataUploadPreviewResponse> preview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("解析完成", dataUploadService.preview(file, user));
    }

    @PostMapping("/{uploadId}/sync")
    public ApiResponse<DataUploadSyncResponse> sync(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("同步完成", dataUploadService.sync(uploadId, user));
    }

    @PostMapping("/{uploadId}/reject")
    public ApiResponse<DataUploadBatchResponse> reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("批次已驳回", dataUploadService.reject(uploadId, user));
    }

    @GetMapping
    public ApiResponse<List<DataUploadBatchResponse>> listBatches(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.listBatches(user));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = securitySupport.requireUser(authorization);
        dataUploadService.requireCanUpload(user);
        byte[] bytes = dataUploadService.createTemplateWorkbook();
        String encodedName = URLEncoder.encode("WBE数据上传模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/{uploadId}/rows")
    public ApiResponse<DataUploadRowsPageResponse> listRows(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.listRows(uploadId, page, size, user));
    }

    @GetMapping("/{uploadId}/file")
    public ResponseEntity<Resource> downloadFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        Path path = dataUploadService.getStoredFile(uploadId, user);
        String fileName = dataUploadService.getFileName(uploadId, user);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }
}
