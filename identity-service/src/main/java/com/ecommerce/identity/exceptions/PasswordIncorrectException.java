package com.ecommerce.identity.exceptions;

import org.springframework.security.authentication.BadCredentialsException;

public class PasswordIncorrectException extends BadCredentialsException {
  public PasswordIncorrectException(String message) {
    super(message);
  }
}
