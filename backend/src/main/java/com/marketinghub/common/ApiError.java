package com.marketinghub.common;

public record ApiError(ApiErrorBody error) {
    public static ApiError of(String code, String message, String traceId) {
        return new ApiError(new ApiErrorBody(code, message, traceId));
    }

    public record ApiErrorBody(String code, String message, String traceId) {}
}
