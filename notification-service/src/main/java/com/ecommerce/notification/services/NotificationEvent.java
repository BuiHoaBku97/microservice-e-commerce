package com.ecommerce.notification.services;

import com.fasterxml.jackson.databind.JsonNode;

public record NotificationEvent(
    Long userId,
    String eventType,
    String title,
    String message,
    String referenceType,
    Long referenceId,
    String deduplicationKey) {
  public static NotificationEvent from(JsonNode payload) {
    String eventType = payload.path("eventType").asText();
    Long userId = payload.path("userId").asLong();
    Long orderId = payload.hasNonNull("orderId") ? payload.get("orderId").asLong() : null;
    Long referenceId =
        orderId != null
            ? orderId
            : payload.hasNonNull("paymentId") ? payload.get("paymentId").asLong() : null;
    String referenceType = orderId != null ? "ORDER" : "PAYMENT";
    String title;
    String message;
    switch (eventType) {
      case "OrderCreated" -> {
        title = "Đơn hàng đã được tạo";
        message = "Đơn hàng #" + referenceId + " đã được tạo và đang chờ thanh toán.";
      }
      case "PaymentCompleted" -> {
        title = "Thanh toán thành công";
        message = "Đơn hàng #" + referenceId + " đã được thanh toán thành công.";
      }
      case "PaymentFailed" -> {
        title = "Thanh toán thất bại";
        message = "Thanh toán cho đơn hàng #" + referenceId + " không thành công.";
      }
      case "OrderCancelled" -> {
        title = "Đơn hàng đã bị hủy";
        message = "Đơn hàng #" + referenceId + " đã được hủy.";
      }
      default -> throw new IllegalArgumentException("Loại sự kiện không được hỗ trợ");
    }
    return new NotificationEvent(
        userId,
        eventType,
        title,
        message,
        referenceType,
        referenceId,
        eventType + ":" + referenceId);
  }
}
