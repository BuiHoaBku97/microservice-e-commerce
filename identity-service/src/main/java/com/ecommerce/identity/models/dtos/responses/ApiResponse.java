package com.ecommerce.identity.models.dtos.responses;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> success(T data, String requestId) {
        return new ApiResponse<>("SUCCESS", "Thực hiện thành công", data, requestId);
    }
}
