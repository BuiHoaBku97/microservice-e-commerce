package com.ecommerce.order.services;

import com.ecommerce.order.configs.CorrelationContext;
import com.ecommerce.order.messaging.events.EventEnvelope;
import com.ecommerce.order.messaging.events.OrderEventPayloads.*;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public OutboxEventService(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueueOrderCreated(Order order) {
    enqueue("OrderCreated", order,
        new OrderCreatedPayload(order.getId(), order.getUserId(), order.getTotalAmount(),
            order.getCurrency(), order.getPaymentDueAt(), order.getOrderNumber(),
            order.getPaymentMethod()), Instant.now());
  }

  @Transactional
  public void enqueueOrderCancelled(Order order) {
    enqueue("OrderCancelled", order,
        new OrderCancelledPayload(order.getId(), order.getUserId(), order.getCancellationReason(),
            order.getCancelledAt(), order.getPaymentDueAt()), Instant.now());
  }

  @Transactional
  public void enqueueOrderConfirmed(Order order) {
    enqueue("OrderConfirmed", order,
        new OrderConfirmedPayload(order.getId(), order.getUserId(), order.getAcceptedPaymentId(),
            order.getTotalAmount(), order.getCurrency(), order.getConfirmedAt()), Instant.now());
  }

  @Transactional
  public void enqueueOrderShipping(Order order) {
    enqueue("OrderShipping", order,
        new OrderShippingPayload(order.getId(), order.getUserId(), order.getShippingAt()),
        Instant.now());
  }

  @Transactional
  public void enqueueOrderCompleted(Order order) {
    enqueue("OrderCompleted", order,
        new OrderCompletedPayload(order.getId(), order.getUserId(), order.getCompletedAt(),
            order.getTotalAmount(), order.getCurrency()), Instant.now());
  }

  @Transactional
  public void enqueueOrderConfirmationRejected(Order order, Long paymentId, String reason) {
    Instant rejectedAt = Instant.now();
    enqueue("OrderConfirmationRejected", order,
        new OrderConfirmationRejectedPayload(order.getId(), paymentId, reason,
            order.getStatus().name(), rejectedAt), rejectedAt);
  }

  private void enqueue(String eventType, Order order, Object businessPayload, Instant occurredAt) {
    try {
      String eventId = UUID.randomUUID().toString();
      String correlationId = CorrelationContext.currentOrCreate();
      var envelope = new EventEnvelope<>(eventId, eventType, 1, "ORDER", order.getId(),
          order.getVersion(), occurredAt, correlationId, businessPayload);
      String eventJson = objectMapper.writeValueAsString(envelope);
      outboxEventRepository.save(
          new OutboxEvent(
              eventId,
              "ORDER",
              order.getId(),
              order.getVersion(),
              eventType,
              1,
              "ecommerce.order.events",
              String.valueOf(order.getId()),
              correlationId,
              occurredAt,
              eventJson));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Khong the tao su kien don hang", exception);
    }
  }
}
