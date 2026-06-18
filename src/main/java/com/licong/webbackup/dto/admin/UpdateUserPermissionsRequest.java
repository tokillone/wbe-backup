package com.licong.webbackup.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdateUserPermissionsRequest {

    @NotBlank(message = "角色不能为空")
    private String role;

    private Boolean canUpload;
    private Boolean canReviewUploads;
    private Boolean canSyncData;
    private Boolean canDownload;
}
