package com.licong.webbackup.exception;

import com.licong.webbackup.common.ApiResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void exposesOnlyStableRecoveryCopyForWorkflowConflicts() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(
                new WorkflowStateException("同步要求 APPROVED，实际为 PENDING_REVIEW")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(409);
        assertThat(response.getBody().getMessage())
                .isEqualTo("当前记录状态已发生变化，请刷新页面后重试")
                .doesNotContain("PENDING_REVIEW");
    }
}
