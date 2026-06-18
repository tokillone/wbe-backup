package com.licong.webbackup.dto.admin;

import com.licong.webbackup.dto.UserResponse;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminUserPageResponse {

    private List<UserResponse> items;
    private int page;
    private int size;
    private long total;
    private int totalPages;
}
