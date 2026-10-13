package com.ecommerce.payment.models.dtos.requests;
import jakarta.validation.constraints.*;
import java.time.Instant;
public record PaymentCallbackRequest(
    @NotBlank String providerEventId, @NotBlank String providerTransactionId,
    @NotNull @Positive Long paymentId, @NotNull @Positive Long orderId,
    @NotNull Result result, @NotNull @Positive Long amount,
    @NotBlank String currency, Instant paidAt) {
  public enum Result { SUCCESS, FAILED }
}
