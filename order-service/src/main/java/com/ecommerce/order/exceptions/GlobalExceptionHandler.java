package com.ecommerce.order.exceptions;

import com.ecommerce.order.models.dtos.ApiErrorResponse;
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
    List<String> errors = exception.getBindingResult().getFieldErrors().stream()
        .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
    return ResponseEntity.badRequest()
        .body(ApiErrorResponse.validation(errors, request.getRequestId()));
  }

  @ExceptionHandler(OrderNotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(
      OrderNotFoundException exception, HttpServletRequest request) {
    return error(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", exception, request);
  }

  @ExceptionHandler(IdempotencyKeyConflictException.class)
  ResponseEntity<ApiErrorResponse> idempotencyConflict(
      IdempotencyKeyConflictException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED", exception, request);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiErrorResponse> badRequest(
      IllegalArgumentException exception, HttpServletRequest request) {
    String value = exception.getMessage();
    String code = value != null && value.matches("[A-Z][A-Z0-9_]+") ? value : "INVALID_REQUEST";
    HttpStatus status = code.endsWith("NOT_FOUND") ? HttpStatus.NOT_FOUND : HttpStatus.BAD_REQUEST;
    return error(status, code, exception, request);
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<ApiErrorResponse> conflict(
      IllegalStateException exception, HttpServletRequest request) {
    String value = exception.getMessage();
    String code = switch (value == null ? "" : value) {
      case "VERSION_CONFLICT", "CART_CHANGED", "SKU_NOT_SELLABLE" -> value;
      default -> "INVALID_STATE";
    };
    return error(HttpStatus.CONFLICT, code, exception, request);
  }

  @ExceptionHandler(PriceChangedException.class)
  ResponseEntity<ApiErrorResponse> priceChanged(
      PriceChangedException exception, HttpServletRequest request) {
    return error(HttpStatus.CONFLICT, "PRICE_CHANGED", exception, request);
  }

  @ExceptionHandler(CatalogUnavailableException.class)
  ResponseEntity<ApiErrorResponse> catalogUnavailable(
      CatalogUnavailableException exception, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", exception, request);
  }

  @ExceptionHandler(IdentityUnavailableException.class)
  ResponseEntity<ApiErrorResponse> identityUnavailable(
      IdentityUnavailableException exception, HttpServletRequest request) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, "IDENTITY_UNAVAILABLE", exception, request);
  }

  private ResponseEntity<ApiErrorResponse> error(
      HttpStatus status, String code, Exception exception, HttpServletRequest request) {
    return ResponseEntity.status(status)
        .body(ApiErrorResponse.of(code, exception.getMessage(), request.getRequestId()));
  }
}
