package com.ecommerce.catalog.exceptions;

import com.ecommerce.catalog.models.dtos.responses.ApiErrorResponse;
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
        .body(ApiErrorResponse.of("METHOD_NOT_ALLOWED", "Phuong thuc HTTP khong duoc ho tro cho duong dan nay", request.getRequestId()));
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

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> notFound(
      IllegalArgumentException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    boolean notFound = code != null && code.endsWith("NOT_FOUND");
    HttpStatus status = notFound ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    String message =
        switch (code == null ? "" : code) {
          case "CATEGORY_NOT_FOUND" -> "Không tìm thấy danh mục";
          case "PRODUCT_NOT_FOUND" -> "Không tìm thấy sản phẩm đang được bán";
          case "SKU_NOT_FOUND" -> "Không tìm thấy SKU";
          default -> "Dữ liệu danh mục hoặc sản phẩm không hợp lệ";
        };
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String code = exception.getMessage();
    String message = switch (code == null ? "" : code) {
      case "SKU_EXISTS" -> "Mã SKU đã tồn tại";
      case "VERSION_CONFLICT" -> "Dữ liệu đã thay đổi, vui lòng tải lại và thử lại";
      case "REINDEX_ALREADY_RUNNING" -> "Tác vụ đồng bộ danh mục đang được thực hiện";
      case "SKU_NOT_SELLABLE" -> "SKU hoặc sản phẩm không còn được bán";
      case "CATEGORY_EXISTS" -> "Danh mục đã tồn tại";
      case "VARIANT_EXISTS" -> "Phiên bản sản phẩm đã tồn tại";
      case "PRODUCT_REQUIRES_ACTIVE_SKU" -> "Sản phẩm cần có ít nhất một SKU hoạt động";
      default -> "Trạng thái danh mục hoặc sản phẩm không hợp lệ";
    };

    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiErrorResponse.of(code, message, request.getRequestId()));
  }
}
