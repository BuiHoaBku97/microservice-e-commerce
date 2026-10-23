package com.ecommerce.notification.exceptions;

import com.ecommerce.notification.models.dtos.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;

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
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.validation(errors, request.getRequestId()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> invalid(
      IllegalArgumentException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    boolean notFound = code != null && code.contains("NOT_FOUND");
    return ResponseEntity.status(notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST)
        .body(ApiErrorResponse.of(
            notFound ? code : (code != null && code.matches("[A-Z][A-Z0-9_]+") ? code : "INVALID_REQUEST"),
            invalidMessage(code), request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> state(IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(code, stateMessage(code), request.getRequestId()));
  }

  private String invalidMessage(String code) {
    return switch (code == null ? "" : code) {
      case "NOTIFICATION_NOT_FOUND" -> "Không tìm thấy thông báo";
      case "EVENT_PAYLOAD_REQUIRED" -> "Sự kiện thông báo thiếu dữ liệu";
      case "EVENT_TYPE_UNSUPPORTED" -> "Loại sự kiện chưa được hỗ trợ";
      case "EVENT_ENVELOPE_INVALID" -> "Định dạng sự kiện không hợp lệ";
      default -> "Dữ liệu yêu cầu không hợp lệ";
    };
  }

  private String stateMessage(String code) {
    return switch (code == null ? "" : code) {
      case "EVENT_ID_PAYLOAD_CONFLICT" -> "Sự kiện đã tồn tại với nội dung khác";
      case "PAYMENT_EVENT_PROCESSING_FAILED", "ORDER_EVENT_PROCESSING_FAILED" -> "Không thể xử lý sự kiện nghiệp vụ";
      default -> "Trạng thái xử lý thông báo không hợp lệ";
    };
  }
}
