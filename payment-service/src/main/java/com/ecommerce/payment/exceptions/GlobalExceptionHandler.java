package com.ecommerce.payment.exceptions;

import com.ecommerce.payment.models.dtos.responses.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> methodNotAllowed(
      HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(ApiErrorResponse.of("METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ cho đường dẫn này", request.getRequestId()));
  }
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> errors = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
    return ResponseEntity.badRequest().body(new ApiErrorResponse(
        "VALIDATION_ERROR", "Request validation failed", null, errors, request.getRequestId()));
  }

  @ExceptionHandler(SecurityException.class)
  ResponseEntity<ApiErrorResponse> unauthenticated(
      SecurityException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiErrorResponse.of(
            "CALLBACK_UNAUTHENTICATED", exception.getMessage(), request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(code, stateMessage(code), request.getRequestId()));
  }

  @ExceptionHandler(PaymentNotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(
      PaymentNotFoundException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(
            ApiErrorResponse.of(
                "RESOURCE_NOT_FOUND", "Không tìm thấy thông tin thanh toán", request.getRequestId()));
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

  private String stateMessage(String code) {
    return switch (code == null ? "" : code) {
      case "PAYMENT_EXPIRED" -> "Khoản thanh toán đã hết hạn";
      case "PAYMENT_NOT_INITIALIZABLE" -> "Khoản thanh toán chưa thể khởi tạo";
      case "PAYMENT_METHOD_UNSUPPORTED" -> "Phương thức thanh toán chưa được hỗ trợ";
      case "CALLBACK_CONFLICT" -> "Callback thanh toán đã tồn tại với dữ liệu khác";
      case "CALLBACK_MISMATCH" -> "Dữ liệu callback không khớp với khoản thanh toán";
      case "PAYMENT_CALLBACK_SECRET_NOT_CONFIGURED" -> "Thanh toán chưa được cấu hình đầy đủ";
      case "VNPAY_NOT_CONFIGURED" -> "Cổng thanh toán chưa được cấu hình";
      default -> "Trạng thái thanh toán không hợp lệ";
    };
  }
}
