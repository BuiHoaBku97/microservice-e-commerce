package com.ecommerce.payment.exceptions;

import com.ecommerce.payment.models.dtos.responses.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(PaymentNotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(
      PaymentNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiErrorResponse.of(
                "RESOURCE_NOT_FOUND", exception.getMessage(), request.getRequestId()));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiErrorResponse> invalid(Exception exception, HttpServletRequest request) {
    return ResponseEntity.badRequest()
        .body(
            ApiErrorResponse.of(
                "VALIDATION_ERROR", "Mã đơn hàng không hợp lệ", request.getRequestId()));
  }
}
