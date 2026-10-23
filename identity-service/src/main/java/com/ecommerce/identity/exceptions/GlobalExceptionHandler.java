package com.ecommerce.identity.exceptions;

import com.ecommerce.identity.models.dtos.responses.ApiErrorResponse;
import jakarta.persistence.Tuple;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
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
    record Response(String message, HttpStatus status) {}
    Response response = switch (code == null ? "" : code) {
      case "EMAIL_EXISTS" -> new Response("Email đã được sử dụng", HttpStatus.CONFLICT);
      case "ACCOUNT_NOT_ACTIVE" -> new Response("Tài khoản không ở trạng thái hoạt động", HttpStatus.FORBIDDEN);
      case "ADMIN_ACCOUNT_BAD_FORMAT" -> new Response("ADMIN_EMAIL and ADMIN_PASSWORD with at least 8 characters are required", HttpStatus.LENGTH_REQUIRED);
      case "LAST_ACTIVE_ADMIN" -> new Response("Không thể khóa quản trị viên đang hoạt động cuối cùng", HttpStatus.CONFLICT);
      case "VERSION_CONFLICT" -> new Response("Phiên bản không hợp lệ", HttpStatus.CONFLICT);
      default -> new Response("Trạng thái yêu cầu không hợp lệ", HttpStatus.CONFLICT);
    };

    return ResponseEntity.status(response.status())
        .body(ApiErrorResponse.of(code, response.message(), request.getRequestId()));
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
