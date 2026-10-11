package com.ecommerce.payment.models.dtos.responses;

import java.util.List;

public record ApiErrorResponse(
    String code, String message, Object data, List<String> errors, String requestId) {
  public static ApiErrorResponse of(String code, String message, String requestId) {
    return new ApiErrorResponse(code, message, null, List.of(), requestId);
  }
}
