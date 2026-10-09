package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.OutboxEvent;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentEventService {
  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;

  public PaymentEventService(OutboxEventRepository events, ObjectMapper objectMapper) {
    this.events = events;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueuePaymentResult(Payment payment) {
    String eventType =
        payment.getStatus().name().equals("COMPLETED") ? "PaymentCompleted" : "PaymentFailed";
    try {
      String payload =
          objectMapper.writeValueAsString(
              Map.of(
                  "eventType",
                  eventType,
                  "paymentId",
                  payment.getId(),
                  "orderId",
                  payment.getOrderId(),
                  "userId",
                  payment.getUserId(),
                  "amount",
                  payment.getAmount(),
                  "currency",
                  payment.getCurrency(),
                  "status",
                  payment.getStatus(),
                  "providerReference",
                  payment.getProviderReference() == null ? "" : payment.getProviderReference(),
                  "failureReason",
                  payment.getFailureReason() == null ? "" : payment.getFailureReason()));
      events.save(new OutboxEvent("Payment", payment.getId(), eventType, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Không thể tạo sự kiện thanh toán", exception);
    }
  }
}
