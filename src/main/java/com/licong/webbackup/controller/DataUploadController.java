package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.common.SecuritySupport;
import com.licong.webbackup.dto.upload.DataUploadBatchPageResponse;
import com.licong.webbackup.dto.upload.DataUploadBatchResponse;
import com.licong.webbackup.dto.upload.DataUploadPreviewResponse;
import com.licong.webbackup.dto.upload.DataUploadReviewDecisionRequest;
import com.licong.webbackup.dto.upload.DataUploadReviewPackageResponse;
import com.licong.webbackup.dto.upload.DataUploadRowsPageResponse;
import com.licong.webbackup.dto.upload.DataUploadSourceReviewRequest;
import com.licong.webbackup.dto.upload.DataUploadSyncResponse;
import com.licong.webbackup.dto.upload.RejectUploadRequest;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.service.DataUploadService;
import com.licong.webbackup.service.Icd11SankeyService;
import com.licong.webbackup.service.SimplifiedDataUploadService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    private final Icd11SankeyService icd11SankeyService;
    private final SimplifiedDataUploadService simplifiedDataUploadService;

    public DataUploadController(SecuritySupport securitySupport,
                                DataUploadService dataUploadService,
                                Icd11SankeyService icd11SankeyService,
                                SimplifiedDataUploadService simplifiedDataUploadService) {
        this.securitySupport = securitySupport;
        this.dataUploadService = dataUploadService;
        this.icd11SankeyService = icd11SankeyService;
        this.simplifiedDataUploadService = simplifiedDataUploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataUploadPreviewResponse> createSubmission(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam("file") MultipartFile file) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("投稿已接收", simplifiedDataUploadService.createSubmission(file, user));
    }

    @PostMapping(value = "/{uploadId}/submission-revisions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataUploadPreviewResponse> createSubmissionRevision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestParam("file") MultipartFile file) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("修订版本已接收", simplifiedDataUploadService.createRevision(uploadId, file, user));
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataUploadPreviewResponse> preview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(value = "allowDuplicate", defaultValue = "false") boolean allowDuplicate,
            @RequestParam("file") MultipartFile file) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("解析完成", dataUploadService.preview(file, user, allowDuplicate));
    }

    @PostMapping("/{uploadId}/approve")
    public ApiResponse<DataUploadBatchResponse> approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestBody DataUploadReviewDecisionRequest request) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("批次已终审通过", dataUploadService.approve(uploadId, user, request));
    }

    @PostMapping("/{uploadId}/source-review/accept")
    public ApiResponse<DataUploadBatchResponse> acceptSourceReview(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestBody(required = false) DataUploadSourceReviewRequest request) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("原始提交已通过初审，等待完整整理包",
                dataUploadService.acceptSourceReview(uploadId, user, request));
    }

    @PostMapping(value = "/{uploadId}/review-packages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<DataUploadReviewPackageResponse> uploadReviewPackage(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestParam(value = "allowDuplicate", defaultValue = "false") boolean allowDuplicate,
            @RequestParam("file") MultipartFile file) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success("五表审核包解析完成",
                simplifiedDataUploadService.uploadReviewPackage(uploadId, file, user));
    }

    @PostMapping("/{uploadId}/return")
    public ApiResponse<DataUploadBatchResponse> returnForRevision(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestBody(required = false) RejectUploadRequest request) {
        User user = securitySupport.requireUser(authorization);
        String reason = request == null ? null : request.getReason();
        return ApiResponse.success("已退回投稿人修改",
                simplifiedDataUploadService.returnForRevision(uploadId, user, reason));
    }

    @PostMapping("/{uploadId}/publish")
    public ApiResponse<DataUploadSyncResponse> publish(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        try {
            DataUploadSyncResponse result = simplifiedDataUploadService.publish(uploadId, user);
            icd11SankeyService.invalidateCache();
            return ApiResponse.success("增量入库完成", result);
        } catch (RuntimeException exception) {
            try {
                simplifiedDataUploadService.recordPublishFailure(uploadId, user, exception.getMessage());
            } catch (RuntimeException auditException) {
                exception.addSuppressed(auditException);
            }
            throw exception;
        }
    }

    @GetMapping("/{uploadId}/review-packages")
    public ApiResponse<List<DataUploadReviewPackageResponse>> listReviewPackages(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.listReviewPackages(uploadId, user));
    }

    @PostMapping("/{uploadId}/sync")
    public ApiResponse<DataUploadSyncResponse> sync(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        try {
            DataUploadSyncResponse response = dataUploadService.sync(uploadId, user);
            icd11SankeyService.invalidateCache();
            return ApiResponse.success("同步完成", response);
        } catch (RuntimeException exception) {
            try {
                dataUploadService.recordSyncFailure(uploadId, user, exception.getMessage());
            } catch (RuntimeException auditException) {
                exception.addSuppressed(auditException);
            }
            throw exception;
        }
    }

    @PostMapping("/{uploadId}/reject")
    public ApiResponse<DataUploadBatchResponse> reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @RequestBody(required = false) RejectUploadRequest request) {
        User user = securitySupport.requireUser(authorization);
        String reason = request == null ? null : request.getReason();
        return ApiResponse.success("批次已驳回", dataUploadService.reject(uploadId, user, reason));
    }

    @GetMapping
    public ApiResponse<DataUploadBatchPageResponse> listBatches(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String uploaderType,
            @RequestParam(defaultValue = "createdAt_desc") String sort) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.listBatches(user, page, size, keyword, status, scope, uploaderType, sort));
    }

    @GetMapping("/{uploadId}")
    public ApiResponse<DataUploadBatchResponse> getBatch(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.getBatch(uploadId, user));
    }

    @GetMapping("/template")
    public ResponseEntity<Resource> downloadTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = securitySupport.requireUser(authorization);
        dataUploadService.requireCanUpload(user);
        byte[] bytes = simplifiedDataUploadService.createSubmissionTemplate();
        String encodedName = URLEncoder.encode("WBE数据上传模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/submission-template")
    public ResponseEntity<Resource> downloadSubmissionTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = securitySupport.requireUser(authorization);
        dataUploadService.requireCanUpload(user);
        byte[] bytes = simplifiedDataUploadService.createSubmissionTemplate();
        String encodedName = URLEncoder.encode("WBE原始数据投稿模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/{uploadId}/review-draft")
    public ResponseEntity<Resource> downloadReviewDraft(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId) {
        User user = securitySupport.requireUser(authorization);
        byte[] bytes = simplifiedDataUploadService.createReviewDraft(uploadId, user);
        String encodedName = URLEncoder.encode("WBE五表审核草稿-" + uploadId + ".xlsx", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentLength(bytes.length)
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new ByteArrayResource(bytes));
    }

    @GetMapping("/review-package-template")
    public ResponseEntity<Resource> downloadReviewPackageTemplate(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        User user = securitySupport.requireUser(authorization);
        dataUploadService.requireCanReviewUploads(user);
        byte[] bytes = dataUploadService.createReviewPackageTemplate();
        String encodedName = URLEncoder.encode("WBE完整整理包模板.xlsx", StandardCharsets.UTF_8).replace("+", "%20");
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
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "active") String rowView) {
        User user = securitySupport.requireUser(authorization);
        return ApiResponse.success(dataUploadService.listRows(uploadId, page, size, status, rowView, user));
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

    @GetMapping("/{uploadId}/review-packages/{packageId}/file")
    public ResponseEntity<Resource> downloadReviewPackageFile(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long uploadId,
            @PathVariable Long packageId) {
        User user = securitySupport.requireUser(authorization);
        Path path = dataUploadService.getReviewPackageFile(uploadId, packageId, user);
        String fileName = dataUploadService.getReviewPackageFileName(uploadId, packageId, user);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(new FileSystemResource(path));
    }
}
