package com.ecommerce.order.services;

import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {
  private final OutboxEventRepository outboxEventRepository;
  private final ObjectMapper objectMapper;

  public OutboxEventService(
      OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
    this.outboxEventRepository = outboxEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public void enqueueOrderCreated(Order order) {
    enqueue("OrderCreated", order);
  }

  @Transactional
  public void enqueueOrderCancelled(Order order) {
    enqueue("OrderCancelled", order);
  }

  @Transactional
  public void enqueueOrderConfirmed(Order order) {
    enqueue("OrderConfirmed", order);
  }

  @Transactional
  public void enqueueOrderShipping(Order order) {
    enqueue("OrderShipping", order);
  }

  @Transactional
  public void enqueueOrderCompleted(Order order) {
    enqueue("OrderCompleted", order);
  }

  @Transactional
  public void enqueueOrderConfirmationRejected(Order order, Long paymentId, String reason) {
    enqueue("OrderConfirmationRejected", order, Map.of(
        "paymentId", paymentId,
        "reason", reason,
        "currentOrderStatus", order.getStatus(),
        "rejectedAt", java.time.Instant.now()));
  }

  private void enqueue(String eventType, Order order) {
    enqueue(eventType, order, Map.of());
  }

  private void enqueue(String eventType, Order order, Map<String, Object> additions) {
    try {
      String eventId = UUID.randomUUID().toString();
      Map<String, Object> eventPayload = new java.util.LinkedHashMap<>(payload(eventId, eventType, order));
      eventPayload.putAll(additions);
      String payload = objectMapper.writeValueAsString(eventPayload);
      outboxEventRepository.save(
          new OutboxEvent(eventId, "Order", order.getId(), eventType, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Không thể tạo sự kiện đơn hàng", exception);
    }
  }

  private Map<String, Object> payload(String eventId, String eventType, Order order) {
    List<Map<String, Object>> items =
        order.getItems().stream()
            .map(
                item ->
                    Map.<String, Object>of(
                        "skuId",
                        item.getSkuId(),
                        "quantity",
                        item.getQuantity(),
                        "unitPrice",
                        item.getUnitPrice(),
                        "lineTotal",
                        item.getLineTotal()))
            .toList();
    return Map.ofEntries(
        Map.entry("eventId", eventId),
        Map.entry("eventType", eventType),
        Map.entry("occurredAt", java.time.Instant.now()),
        Map.entry("aggregateType", "Order"),
        Map.entry("aggregateId", order.getId()),
        Map.entry("orderId", order.getId()),
        Map.entry("userId", order.getUserId()),
        Map.entry("status", order.getStatus()),
        Map.entry("totalAmount", order.getTotalAmount()),
        Map.entry("currency", order.getCurrency()),
        Map.entry("paymentDueAt", order.getPaymentDueAt()),
        Map.entry("paymentId", order.getAcceptedPaymentId() == null ? 0L : order.getAcceptedPaymentId()),
        Map.entry("reason", order.getCancellationReason() == null ? "" : order.getCancellationReason()),
        Map.entry("items", items));
  }
}
