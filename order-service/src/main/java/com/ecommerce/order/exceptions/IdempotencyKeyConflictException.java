package com.ecommerce.order.exceptions;

public class IdempotencyKeyConflictException extends RuntimeException {
  public IdempotencyKeyConflictException() {
    super("Idempotency-Key đã được sử dụng cho nội dung request khác");
  }
}
