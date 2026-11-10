package com.btrepolens.common;
import java.time.Instant;
public record ApiResponse<T>(boolean success, T data, String message, String errorCode, Instant timestamp) {
    public static <T> ApiResponse<T> ok(T data, String message) { return new ApiResponse<>(true, data, message, null, Instant.now()); }
    public static ApiResponse<Void> error(String message, String code) { return new ApiResponse<>(false, null, message, code, Instant.now()); }
}
