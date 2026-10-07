package com.ecommerce.catalog.exceptions;

import com.ecommerce.catalog.models.dtos.responses.ApiResponse;
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

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiResponse<Void>> notFound(
      IllegalArgumentException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    boolean notFound = code != null && code.endsWith("NOT_FOUND");
    HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.CONFLICT;
    String message =
        switch (code == null ? "" : code) {
          case "CATEGORY_NOT_FOUND" -> "Không tìm thấy danh mục";
          case "PRODUCT_NOT_FOUND" -> "Không tìm thấy sản phẩm đang được bán";
          default -> "Dữ liệu danh mục hoặc sản phẩm không hợp lệ";
        };
    return ResponseEntity.status(status)
        .body(new ApiResponse<>(code, message, null, request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiResponse<Void>> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    String message = "SKU_EXISTS".equals(code) ? "Mã SKU đã tồn tại" : "Danh mục đã tồn tại";
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ApiResponse<>(code, message, null, request.getRequestId()));
  }
}
