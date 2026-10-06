package com.ecommerce.identity.exceptions;

import com.ecommerce.identity.models.dtos.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<List<String>>> validation(MethodArgumentNotValidException exception,
                                                         HttpServletRequest request) {
        List<String> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .toList();
        return ResponseEntity.badRequest().body(new ApiResponse<>("VALIDATION_ERROR",
                "Dữ liệu yêu cầu không hợp lệ", errors, request.getRequestId()));
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<ApiResponse<Void>> conflict(IllegalStateException exception,
                                               HttpServletRequest request) {
        String message = "EMAIL_EXISTS".equals(exception.getMessage())
                ? "Email đã được sử dụng" : "Trạng thái yêu cầu không hợp lệ";
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ApiResponse<>("EMAIL_EXISTS", message, null, request.getRequestId()));
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<ApiResponse<Void>> credentials(BadCredentialsException exception,
                                                  HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new ApiResponse<>("UNAUTHENTICATED", "Thông tin đăng nhập không hợp lệ", null,
                        request.getRequestId()));
    }
}
