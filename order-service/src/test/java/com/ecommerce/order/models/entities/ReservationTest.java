package com.ecommerce.order.models.entities;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ReservationTest {
  @Test
  void commitAndReleaseAreIdempotentButCannotReverseTerminalState() {
    Reservation committed = new Reservation(1L, Instant.now().plusSeconds(60));
    assertThat(committed.commit()).isTrue();
    assertThat(committed.commit()).isFalse();
    assertThatThrownBy(() -> committed.release("CANCELLED"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("RESERVATION_ALREADY_COMMITTED");

    Reservation released = new Reservation(2L, Instant.now().plusSeconds(60));
    assertThat(released.release("PAYMENT_EXPIRED")).isTrue();
    assertThat(released.release("PAYMENT_EXPIRED")).isFalse();
    assertThatThrownBy(released::commit)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("RESERVATION_ALREADY_RELEASED");
  }
}
