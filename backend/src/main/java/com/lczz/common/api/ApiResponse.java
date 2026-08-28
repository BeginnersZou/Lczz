package com.lczz.common.api;

import java.time.Instant;

public record ApiResponse<T>(int code, String message, T data, String error, String requestId, Instant timestamp) {

    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>(200, "success", data, null, requestId, Instant.now());
    }

    public static ApiResponse<Void> failure(int status, String error, String message, String requestId) {
        return new ApiResponse<>(status, message, null, error, requestId, Instant.now());
    }
}
