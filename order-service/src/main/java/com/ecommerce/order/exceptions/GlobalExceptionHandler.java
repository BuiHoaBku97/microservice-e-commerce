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
        exception.getMessage().equals("VERSION_CONFLICT") ? "VERSION_CONFLICT" : "INVALID_STATE",
        "message",
        exception.getMessage());
  }
}
