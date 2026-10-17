package com.ecommerce.order.models.dtos;

public record ApiResponse<T>(String code, String message, T data, String requestId) {
  public static <T> ApiResponse<T> success(T data, String requestId) {
    return new ApiResponse<>("SUCCESS", "Success", data, requestId);
  }
}
