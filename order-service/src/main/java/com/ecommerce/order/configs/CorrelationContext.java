package com.ecommerce.order.configs;

import java.util.UUID;
import org.slf4j.MDC;

public final class CorrelationContext {
  public static final String HEADER = "X-Correlation-ID";
  private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

  private CorrelationContext() {}

  public static String currentOrCreate() {
    String value = CURRENT.get();
    if (value == null) set(UUID.randomUUID().toString());
    return CURRENT.get();
  }

  public static void set(String value) {
    if (!isValid(value)) throw new IllegalArgumentException("CORRELATION_ID_INVALID");
    CURRENT.set(value);
    MDC.put("correlationId", value);
  }

  public static boolean isValid(String value) {
    return value != null && !value.isBlank() && value.length() <= 128;
  }

  public static void clear() {
    CURRENT.remove();
    MDC.remove("correlationId");
  }
}
