package com.ecommerce.order.exceptions;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  @ExceptionHandler(OrderNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, Object> notFound(OrderNotFoundException exception) {
    return Map.of("code", "ORDER_NOT_FOUND", "message", exception.getMessage());
  }

  @ExceptionHandler(IdempotencyKeyConflictException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> idempotencyConflict(IdempotencyKeyConflictException exception) {
    return Map.of("code", "IDEMPOTENCY_KEY_REUSED", "message", exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> badRequest(IllegalArgumentException exception) {
    return Map.of("code", "INVALID_REQUEST", "message", exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> conflict(IllegalStateException exception) {
    return Map.of(
        "code",
        "VERSION_CONFLICT".equals(exception.getMessage())
            ? "VERSION_CONFLICT"
            : "CART_CHANGED".equals(exception.getMessage())
                ? "CART_CHANGED"
                : "SKU_NOT_SELLABLE".equals(exception.getMessage())
                    ? "SKU_NOT_SELLABLE"
                    : "INVALID_STATE",
        "message",
        exception.getMessage());
  }

  @ExceptionHandler(PriceChangedException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public Map<String, Object> priceChanged(PriceChangedException exception) {
    return Map.of("code", "PRICE_CHANGED", "message", exception.getMessage());
  }

  @ExceptionHandler(CatalogUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, Object> catalogUnavailable(CatalogUnavailableException exception) {
    return Map.of("code", "CATALOG_UNAVAILABLE", "message", exception.getMessage());
  }

  @ExceptionHandler(IdentityUnavailableException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public Map<String, Object> identityUnavailable(IdentityUnavailableException exception) {
    return Map.of("code", "IDENTITY_UNAVAILABLE", "message", exception.getMessage());
  }
}
