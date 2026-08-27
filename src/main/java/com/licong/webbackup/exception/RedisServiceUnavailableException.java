package com.licong.webbackup.exception;

public class RedisServiceUnavailableException extends RuntimeException {

    public RedisServiceUnavailableException(String operation, Throwable cause) {
        super("Redis operation failed: " + operation, cause);
    }
}
