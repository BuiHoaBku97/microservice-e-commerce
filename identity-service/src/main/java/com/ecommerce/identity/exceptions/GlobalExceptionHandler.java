package com.ecommerce.identity.exceptions;

import com.ecommerce.identity.models.dtos.responses.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ApiErrorResponse> methodNotAllowed(HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
    return response(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Phương thức HTTP không được hỗ trợ cho đường dẫn này", request);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
    List<String> errors = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
    return ResponseEntity.badRequest().body(ApiErrorResponse.validation(errors, request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    Response mapped = switch (code == null ? "" : code) {
      case "EMAIL_EXISTS" -> new Response(HttpStatus.CONFLICT, "EMAIL_EXISTS", "Email đã được sử dụng");
      case "ACCOUNT_NOT_ACTIVE" -> new Response(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Tài khoản chưa được kích hoạt");
      case "ADMIN_ACCOUNT_BAD_FORMAT" -> new Response(HttpStatus.BAD_REQUEST, "ADMIN_ACCOUNT_BAD_FORMAT", "Thông tin tài khoản quản trị mặc định không hợp lệ");
      case "LAST_ACTIVE_ADMIN" -> new Response(HttpStatus.CONFLICT, "LAST_ACTIVE_ADMIN", "Không thể khóa quản trị viên đang hoạt động cuối cùng");
      case "VERSION_CONFLICT" -> new Response(HttpStatus.CONFLICT, "VERSION_CONFLICT", "Dữ liệu đã thay đổi, vui lòng tải lại và thử lại");
      default -> new Response(HttpStatus.CONFLICT, "INVALID_STATE", "Trạng thái yêu cầu không hợp lệ");
    };
    return response(mapped.status(), mapped.code(), mapped.message(), request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> badRequest(IllegalArgumentException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    Response mapped = switch (code == null ? "" : code) {
      case "ADDRESS_NOT_FOUND" -> new Response(HttpStatus.NOT_FOUND, "ADDRESS_NOT_FOUND", "Không tìm thấy địa chỉ của tài khoản");
      case "USER_NOT_FOUND" -> new Response(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "Không tìm thấy tài khoản");
      case "JWT_EXPIRED" -> new Response(HttpStatus.UNAUTHORIZED, "JWT_EXPIRED", "Phiên đăng nhập đã hết hạn");
      case "JWT_ISSUER_OR_AUDIENCE_INVALID" -> new Response(HttpStatus.UNAUTHORIZED, "JWT_ISSUER_OR_AUDIENCE_INVALID", "Token đăng nhập không hợp lệ");
      case "REFRESH_TOKEN_INVALID" -> new Response(HttpStatus.UNAUTHORIZED, "REFRESH_TOKEN_INVALID", "Refresh token không hợp lệ hoặc đã hết hạn");
      case "CORRELATION_ID_INVALID" -> new Response(HttpStatus.BAD_REQUEST, "CORRELATION_ID_INVALID", "Mã tương quan không hợp lệ");
      case "SERVICE_JWT_KEY_OWNER_MISMATCH", "INVALID_SERVICE_JWT_KEY_OWNER" -> new Response(HttpStatus.UNAUTHORIZED, code, "Thông tin xác thực nội bộ không hợp lệ");
      default -> new Response(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Dữ liệu yêu cầu không hợp lệ");
    };
    return response(mapped.status(), mapped.code(), mapped.message(), request);
  }

  @ExceptionHandler(PasswordIncorrectException.class)
  ResponseEntity<ApiErrorResponse> passwordIncorrect(PasswordIncorrectException exception, HttpServletRequest request) {
    return response(HttpStatus.UNAUTHORIZED, "PASSWORD_INCORRECT", "Mật khẩu không đúng", request);
  }

  @ExceptionHandler(BadCredentialsException.class)
  ResponseEntity<ApiErrorResponse> credentials(BadCredentialsException exception, HttpServletRequest request) {
    return response(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "Thông tin đăng nhập không hợp lệ", request);
  }

  @ExceptionHandler(AccountNotActiveException.class)
  ResponseEntity<ApiErrorResponse> accountNotActive(AccountNotActiveException exception, HttpServletRequest request) {
    return response(HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE", "Tài khoản chưa được kích hoạt", request);
  }

  private ResponseEntity<ApiErrorResponse> response(HttpStatus status, String code, String message, HttpServletRequest request) {
    return ResponseEntity.status(status).body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }

  private record Response(HttpStatus status, String code, String message) {}
}
