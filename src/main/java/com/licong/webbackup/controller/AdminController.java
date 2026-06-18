package com.licong.webbackup.controller;

import com.licong.webbackup.common.ApiResponse;
import com.licong.webbackup.common.SecuritySupport;
import com.licong.webbackup.dto.UserResponse;
import com.licong.webbackup.dto.admin.AdminUserPageResponse;
import com.licong.webbackup.dto.admin.BulkUpdateUserPermissionsRequest;
import com.licong.webbackup.dto.admin.BulkUpdateUserPermissionsResponse;
import com.licong.webbackup.dto.admin.UpdateUserPermissionsRequest;
import com.licong.webbackup.entity.User;
import com.licong.webbackup.exception.BusinessException;
import com.licong.webbackup.mapper.UserMapper;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Set<String> VALID_ROLES = Set.of("admin", "editor", "viewer");

    private final SecuritySupport securitySupport;
    private final UserMapper userMapper;

    public AdminController(SecuritySupport securitySupport, UserMapper userMapper) {
        this.securitySupport = securitySupport;
        this.userMapper = userMapper;
    }

    @GetMapping("/users")
    public ApiResponse<AdminUserPageResponse> listUsers(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean canUpload,
            @RequestParam(required = false) Boolean canReviewUploads,
            @RequestParam(required = false) Boolean canSyncData,
            @RequestParam(required = false) Boolean canDownload) {
        securitySupport.requireAdmin(authorization);
        int normalizedPage = Math.max(1, page);
        int normalizedSize = normalizePageSize(size);
        String normalizedRole = normalizeRoleFilter(role);
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = userMapper.countPage(
                normalizedKeyword,
                normalizedRole,
                canUpload,
                canReviewUploads,
                canSyncData,
                canDownload
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / normalizedSize);
        int offset = (normalizedPage - 1) * normalizedSize;
        List<UserResponse> items = userMapper.findPage(
                        normalizedKeyword,
                        normalizedRole,
                        canUpload,
                        canReviewUploads,
                        canSyncData,
                        canDownload,
                        normalizedSize,
                        offset
                )
                .stream()
                .map(UserResponse::from)
                .toList();
        return ApiResponse.success(AdminUserPageResponse.builder()
                .items(items)
                .page(normalizedPage)
                .size(normalizedSize)
                .total(total)
                .totalPages(totalPages)
                .build());
    }

    @PutMapping("/users/{userId}/permissions")
    public ApiResponse<UserResponse> updateUserPermissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserPermissionsRequest request) {
        User currentUser = securitySupport.requireAdmin(authorization);
        return ApiResponse.success("用户权限已更新", updateOneUserPermissions(currentUser, userId, request));
    }

    @PutMapping("/users/{userId}/role")
    public ApiResponse<UserResponse> updateUserRole(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable Long userId,
            @Valid @RequestBody UpdateUserPermissionsRequest request) {
        User currentUser = securitySupport.requireAdmin(authorization);
        return ApiResponse.success("用户权限已更新", updateOneUserPermissions(currentUser, userId, request));
    }

    @PutMapping("/users/bulk-permissions")
    public ApiResponse<BulkUpdateUserPermissionsResponse> bulkUpdateUserPermissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @Valid @RequestBody BulkUpdateUserPermissionsRequest request) {
        securitySupport.requireAdmin(authorization);
        List<Long> userIds = new ArrayList<>(new LinkedHashSet<>(
                request.getUserIds().stream().filter(Objects::nonNull).toList()
        ));
        if (userIds.isEmpty()) {
            throw new BusinessException("请选择需要操作的用户");
        }
        String role = normalizeRequestedRole(request.getRole(), false);
        List<UserResponse> updatedUsers = new ArrayList<>();
        for (Long userId : userIds) {
            User target = requireTargetUser(userId);
            if ("admin".equals(target.getRole())) {
                throw new BusinessException("不能批量修改系统管理员");
            }
            String nextRole = role == null ? target.getRole() : role;
            userMapper.updatePermissions(
                    userId,
                    nextRole,
                    request.getCanUpload() == null ? effectiveCanUpload(target) : request.getCanUpload(),
                    request.getCanReviewUploads() == null ? effectiveCanReviewUploads(target) : request.getCanReviewUploads(),
                    request.getCanSyncData() == null ? effectiveCanSyncData(target) : request.getCanSyncData(),
                    request.getCanDownload() == null ? effectiveCanDownload(target) : request.getCanDownload()
            );
            updatedUsers.add(UserResponse.from(userMapper.findById(userId)));
        }
        return ApiResponse.success("批量权限已更新", BulkUpdateUserPermissionsResponse.builder()
                .updatedCount(updatedUsers.size())
                .updatedUsers(updatedUsers)
                .build());
    }

    private UserResponse updateOneUserPermissions(User currentUser, Long userId, UpdateUserPermissionsRequest request) {
        String role = normalizeRequestedRole(request.getRole(), true);
        User target = requireTargetUser(userId);
        if ("admin".equals(target.getRole())) {
            throw new BusinessException("系统管理员权限不可通过普通赋权表调整");
        }
        if ("admin".equals(role)) {
            throw new BusinessException("不能通过该界面授予系统管理员权限");
        }
        if (currentUser.getUserId().equals(userId) && !"admin".equals(role)) {
            throw new BusinessException("不能取消自己的管理员权限");
        }
        userMapper.updatePermissions(
                userId,
                role,
                request.getCanUpload() == null ? effectiveCanUpload(target) : request.getCanUpload(),
                request.getCanReviewUploads() == null ? effectiveCanReviewUploads(target) : request.getCanReviewUploads(),
                request.getCanSyncData() == null ? effectiveCanSyncData(target) : request.getCanSyncData(),
                request.getCanDownload() == null ? effectiveCanDownload(target) : request.getCanDownload()
        );
        return UserResponse.from(userMapper.findById(userId));
    }

    private String normalizeRequestedRole(String role, boolean required) {
        if (role == null || role.isBlank()) {
            if (required) {
                throw new BusinessException("角色不能为空");
            }
            return null;
        }
        String normalized = role.trim();
        if (!VALID_ROLES.contains(normalized)) {
            throw new BusinessException("角色只能是 admin、editor 或 viewer");
        }
        if ("admin".equals(normalized)) {
            throw new BusinessException("不能通过该界面授予系统管理员权限");
        }
        return normalized;
    }

    private User requireTargetUser(Long userId) {
        User target = userMapper.findById(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        return target;
    }

    private String normalizeRoleFilter(String role) {
        if (role == null || role.isBlank() || "all".equals(role)) {
            return null;
        }
        String normalized = role.trim();
        if (!VALID_ROLES.contains(normalized)) {
            throw new BusinessException("角色只能是 admin、editor 或 viewer");
        }
        return normalized;
    }

    private int normalizePageSize(int size) {
        if (size == 10 || size == 20 || size == 50) {
            return size;
        }
        return 10;
    }

    private boolean effectiveCanUpload(User user) {
        return "admin".equals(user.getRole()) || (user.getCanUpload() == null
                ? "editor".equals(user.getRole())
                : Boolean.TRUE.equals(user.getCanUpload()));
    }

    private boolean effectiveCanReviewUploads(User user) {
        return "admin".equals(user.getRole()) || (user.getCanReviewUploads() == null
                ? "editor".equals(user.getRole())
                : Boolean.TRUE.equals(user.getCanReviewUploads()));
    }

    private boolean effectiveCanSyncData(User user) {
        return "admin".equals(user.getRole()) || (user.getCanSyncData() == null
                ? "editor".equals(user.getRole())
                : Boolean.TRUE.equals(user.getCanSyncData()));
    }

    private boolean effectiveCanDownload(User user) {
        return user.getCanDownload() == null || Boolean.TRUE.equals(user.getCanDownload());
    }
}
