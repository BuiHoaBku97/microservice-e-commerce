package com.ecommerce.payment.services;

import com.ecommerce.payment.configs.CorrelationContext;
import com.ecommerce.payment.messaging.events.EventEnvelope;
import com.ecommerce.payment.messaging.events.PaymentEventPayloads.PaymentFailedPayload;
import com.ecommerce.payment.messaging.events.PaymentEventPayloads.PaymentSucceededPayload;
import com.ecommerce.payment.models.entities.FundsStatus;
import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventService {
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public PaymentEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueuePaymentResult(Payment payment) {
    boolean succeeded = payment.getFundsStatus() == FundsStatus.RECEIVED;
    String eventType = succeeded ? "PaymentSucceeded" : "PaymentFailed";
    Instant occurredAt = Instant.now();
    Object businessPayload =
        succeeded
            ? new PaymentSucceededPayload(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getProviderTransactionId(),
                payment.getPaidAt())
            : new PaymentFailedPayload(
                payment.getId(),
                payment.getOrderId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getFailedAt() == null ? occurredAt : payment.getFailedAt(),
                payment.getFailureReason());
    try {
      String eventId = UUID.randomUUID().toString();
      String correlationId = CorrelationContext.currentOrCreate();
      var envelope =
          new EventEnvelope<>(
              eventId,
              eventType,
              1,
              "PAYMENT",
              payment.getId(),
              payment.getVersion(),
              occurredAt,
              correlationId,
              businessPayload);
      String eventJson = objectMapper.writeValueAsString(envelope);
      outboxEventRepository.save(
          new OutboxEvent(
              eventId,
              "PAYMENT",
              payment.getId(),
              payment.getVersion(),
              eventType,
              1,
              "ecommerce.payment.events",
              String.valueOf(payment.getOrderId()),
              correlationId,
              occurredAt,
              eventJson));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("PAYMENT_EVENT_CREATION_FAILED", exception);
    }
  }
}
