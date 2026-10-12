package com.ecommerce.order.exceptions;

public class IdentityUnavailableException extends RuntimeException {
  public IdentityUnavailableException() {
    super("Identity service hiện không khả dụng");
  }
}
