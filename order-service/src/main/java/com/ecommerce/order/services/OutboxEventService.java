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

  private void enqueue(String eventType, Order order) {
    try {
      String eventId = UUID.randomUUID().toString();
      String payload = objectMapper.writeValueAsString(payload(eventId, eventType, order));
      outboxEventRepository.save(new OutboxEvent("Order", order.getId(), eventType, payload));
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
        Map.entry("items", items));
  }
}
