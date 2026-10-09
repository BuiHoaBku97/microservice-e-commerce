package com.ecommerce.order.services;

import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OutboxEvent;
import com.ecommerce.order.repositories.OutboxEventRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OutboxEventService {
  private final OutboxEventRepository events;
  private final ObjectMapper objectMapper;

  public OutboxEventService(OutboxEventRepository events, ObjectMapper objectMapper) {
    this.events = events;
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

  private void enqueue(String eventType, Order order) {
    try {
      String payload = objectMapper.writeValueAsString(payload(eventType, order));
      events.save(new OutboxEvent("Order", order.getId(), eventType, payload));
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Không thể tạo sự kiện đơn hàng", exception);
    }
  }

  private Map<String, Object> payload(String eventType, Order order) {
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
    return Map.of(
        "eventType",
        eventType,
        "orderId",
        order.getId(),
        "userId",
        order.getUserId(),
        "status",
        order.getStatus(),
        "totalAmount",
        order.getTotalAmount(),
        "currency",
        order.getCurrency(),
        "items",
        items);
  }
}
