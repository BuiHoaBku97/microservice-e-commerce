package com.ecommerce.notification.exceptions;

import com.ecommerce.notification.models.dtos.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
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
            notFound ? code : "INVALID_REQUEST", exception.getMessage(), request.getRequestId()));
  }
}
