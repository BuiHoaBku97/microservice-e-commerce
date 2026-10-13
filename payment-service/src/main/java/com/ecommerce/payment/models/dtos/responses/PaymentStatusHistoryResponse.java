package com.ecommerce.payment.models.dtos.responses;

import com.ecommerce.payment.models.entities.PaymentStatusHistory;
import java.time.Instant;

public record PaymentStatusHistoryResponse(
    Long id, String status, String fundsStatus, String orderAcceptance,
    String source, String reason, Instant createdAt) {
  public static PaymentStatusHistoryResponse from(PaymentStatusHistory history) {
    return new PaymentStatusHistoryResponse(
        history.getId(), history.getStatus().name(), history.getFundsStatus().name(),
        history.getOrderAcceptance().name(), history.getSource(), history.getReason(),
        history.getCreatedAt());
  }
}
