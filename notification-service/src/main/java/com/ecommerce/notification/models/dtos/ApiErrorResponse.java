package com.ecommerce.notification.models.dtos;

import java.util.List;

public record ApiErrorResponse(
    String code, String message, Object data, List<String> errors, String requestId) {
  public static ApiErrorResponse of(String code, String message, String requestId) {
    return new ApiErrorResponse(code, message, null, List.of(), requestId);
  }

  public static ApiErrorResponse validation(List<String> errors, String requestId) {
    return new ApiErrorResponse(
        "VALIDATION_ERROR", "Request validation failed", null, errors, requestId);
  }
}
