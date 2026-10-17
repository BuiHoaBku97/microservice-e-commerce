package com.ecommerce.gateway.configs;

import java.util.List;

public record GatewayErrorResponse(
    String code, String message, Object data, List<String> errors, String requestId) {
  public GatewayErrorResponse(String code, String message, Object data, String requestId) {
    this(code, message, data, List.of(), requestId);
  }
}
