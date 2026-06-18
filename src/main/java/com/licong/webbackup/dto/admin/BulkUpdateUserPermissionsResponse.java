package com.licong.webbackup.dto.admin;

import com.licong.webbackup.dto.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class BulkUpdateUserPermissionsResponse {

    private int updatedCount;
    private List<UserResponse> updatedUsers;
}
