package com.ecommerce.gateway.configs;

public record GatewayErrorResponse(String code, String message, Object data, String requestId) {}
