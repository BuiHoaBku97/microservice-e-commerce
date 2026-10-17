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

  @Test
  void lifecycleTransitionsKeepTheirAuthoritativeTimestamps() {
    Instant confirmedAt = DEADLINE.minusSeconds(30);
    Instant shippingAt = DEADLINE.plusSeconds(30);
    Instant completedAt = DEADLINE.plusSeconds(60);
    Order order = new Order(7L, 100_000L, "VND", DEADLINE);

    order.confirm(20L, confirmedAt);
    order.confirm(21L, DEADLINE);
    order.ship(shippingAt);
    order.ship(DEADLINE.plusSeconds(40));
    order.complete(completedAt);
    order.complete(DEADLINE.plusSeconds(70));

    assertThat(order.getConfirmedAt()).isEqualTo(confirmedAt);
    assertThat(order.getAcceptedPaymentId()).isEqualTo(20L);
    assertThat(order.getShippingAt()).isEqualTo(shippingAt);
    assertThat(order.getCompletedAt()).isEqualTo(completedAt);
  }

  @Test
  void cancellationStoresTheDecisionTime() {
    Order order = new Order(7L, 100_000L, "VND", DEADLINE);

    order.markPaymentFailed(DEADLINE);

    assertThat(order.getCancellationReason()).isEqualTo("PAYMENT_FAILED");
    assertThat(order.getCancelledAt()).isEqualTo(DEADLINE);
  }
}
