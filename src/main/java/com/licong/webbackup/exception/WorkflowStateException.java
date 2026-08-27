package com.licong.webbackup.exception;

import lombok.Getter;

/** Internal workflow detail stays in logs; clients receive one stable recovery message. */
@Getter
public class WorkflowStateException extends BusinessException {

    private final String detail;

    public WorkflowStateException(String detail) {
        super(409, "当前记录状态已发生变化，请刷新页面后重试");
        this.detail = detail;
    }
}
