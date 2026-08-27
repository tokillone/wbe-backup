package com.licong.webbackup.service.impl;

import com.licong.webbackup.exception.RedisServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@Slf4j
public class RedisOperationExecutor {

    public <T> T execute(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            log.error("Redis 操作失败: {}", operation, ex);
            throw new RedisServiceUnavailableException(operation, ex);
        }
    }

    public void execute(String operation, Runnable action) {
        execute(operation, () -> {
            action.run();
            return null;
        });
    }
}
