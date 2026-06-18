package com.licong.webbackup.dto.admin;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BulkUpdateUserPermissionsRequest {

    @NotEmpty(message = "请选择需要操作的用户")
    private List<Long> userIds;

    private String role;
    private Boolean canUpload;
    private Boolean canReviewUploads;
    private Boolean canSyncData;
    private Boolean canDownload;
}
