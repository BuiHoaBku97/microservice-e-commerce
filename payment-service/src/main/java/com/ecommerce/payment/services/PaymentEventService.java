package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.Map;
import java.util.UUID;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventService {
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public PaymentEventService(
      OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueuePaymentResult(Payment payment) {
    String eventType =
        payment.getStatus().name().equals("COMPLETED") ? "PaymentSucceeded" : "PaymentFailed";
    try {
      String eventId = UUID.randomUUID().toString();
      String payload =
          objectMapper.writeValueAsString(
               Map.ofEntries(
                  Map.entry("eventId", eventId),
                  Map.entry("eventType", eventType),
                  Map.entry("occurredAt", Instant.now()),
                  Map.entry("aggregateType", "Payment"),
                  Map.entry("aggregateId", payment.getId()),
                  Map.entry("paymentId", payment.getId()),
                  Map.entry("orderId", payment.getOrderId()),
                  Map.entry("userId", payment.getUserId()),
                  Map.entry("amount", payment.getAmount()),
                  Map.entry("currency", payment.getCurrency()),
                  Map.entry("paymentDueAt", payment.getPaymentDueAt()),
                  Map.entry("status", payment.getStatus()),
                  Map.entry("providerReference", payment.getProviderReference() == null ? "" : payment.getProviderReference()),
                  Map.entry("failureReason", payment.getFailureReason() == null ? "" : payment.getFailureReason())));
      outboxEventRepository.save(
          new OutboxEvent(eventId, "Payment", payment.getId(), eventType, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Không thể tạo sự kiện thanh toán", exception);
    }
  }
}
