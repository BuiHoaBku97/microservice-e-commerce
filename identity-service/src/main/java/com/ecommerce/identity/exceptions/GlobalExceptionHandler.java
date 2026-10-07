package com.ecommerce.identity.exceptions;

import com.ecommerce.identity.models.dtos.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiResponse<List<String>>> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest()
        .body(
            new ApiResponse<>(
                "VALIDATION_ERROR",
                "Dữ liệu yêu cầu không hợp lệ",
                errors,
                request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiResponse<Void>> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String message =
        "EMAIL_EXISTS".equals(exception.getMessage())
            ? "Email đã được sử dụng"
            : "Trạng thái yêu cầu không hợp lệ";
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiResponse<>("EMAIL_EXISTS", message, null, request.getRequestId()));
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiResponse<Void>> credentials(
      BadCredentialsException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            new ApiResponse<>(
                "PASSWORD_INCORRECT", "Mật khẩu không đúng", null, request.getRequestId()));
  }

  @ExceptionHandler(AccountNotActiveException.class)
  ResponseEntity<ApiResponse<Void>> accountNotActive(
      AccountNotActiveException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            new ApiResponse<>(
                "ACCOUNT_NOT_ACTIVE",
                "Tài khoản không ở trạng thái hoạt động",
                null,
                request.getRequestId()));
  }
}
