package com.ecommerce.identity.exceptions;

import com.ecommerce.identity.models.dtos.responses.ApiErrorResponse;
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
  ResponseEntity<ApiErrorResponse> validation(
      MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.validation(errors, request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    if ("ACCOUNT_NOT_ACTIVE".equals(code)) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN)
          .body(
              ApiErrorResponse.of(code, "Tài khoản không ở trạng thái hoạt động", request.getRequestId()));
    }
    if ("LAST_ACTIVE_ADMIN".equals(code)) {
      return ResponseEntity.status(HttpStatus.CONFLICT)
          .body(
              ApiErrorResponse.of(code, "Không thể khóa quản trị viên đang hoạt động cuối cùng",
                  request.getRequestId()));
    }
    String message =
        "EMAIL_EXISTS".equals(code) ? "Email đã được sử dụng" : "Trạng thái yêu cầu không hợp lệ";
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> badRequest(
      IllegalArgumentException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    String message =
        "ADDRESS_NOT_FOUND".equals(code)
            ? "Không tìm thấy địa chỉ của tài khoản"
            : "USER_NOT_FOUND".equals(code)
                ? "Không tìm thấy tài khoản"
                : "Dữ liệu yêu cầu không hợp lệ";
    HttpStatus status = "ADDRESS_NOT_FOUND".equals(code) || "USER_NOT_FOUND".equals(code)
        ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiErrorResponse> credentials(
      BadCredentialsException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(
            ApiErrorResponse.of("PASSWORD_INCORRECT", "Mật khẩu không đúng", request.getRequestId()));
  }

  @ExceptionHandler(AccountNotActiveException.class)
  ResponseEntity<ApiErrorResponse> accountNotActive(
      AccountNotActiveException exception, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.FORBIDDEN)
        .body(
            ApiErrorResponse.of("ACCOUNT_NOT_ACTIVE",
                "Tài khoản không ở trạng thái hoạt động", request.getRequestId()));
  }
}
