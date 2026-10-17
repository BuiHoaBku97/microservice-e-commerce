package com.ecommerce.order.models.entities;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class OrderTest {
  private static final Instant DEADLINE = Instant.parse("2026-10-17T10:15:00Z");

  @Test
  void paymentExpiresAtTheDeadlineBoundary() {
    Order order = new Order(7L, 100_000L, "VND", DEADLINE);

    assertThat(order.isPaymentExpiredAt(DEADLINE.minusNanos(1))).isFalse();
    assertThat(order.isPaymentExpiredAt(DEADLINE)).isTrue();
    assertThat(order.isPaymentExpiredAt(DEADLINE.plusNanos(1))).isTrue();
  }

  @Test
  void paymentExpiryRequiresAnExplicitDecisionTime() {
    Order order = new Order(7L, 100_000L, "VND", DEADLINE);

    assertThatNullPointerException()
        .isThrownBy(() -> order.isPaymentExpiredAt(null))
        .withMessage("decisionTime");
  }
}
