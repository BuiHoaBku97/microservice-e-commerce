package com.ecommerce.notification.messaging.events;

import com.fasterxml.jackson.databind.JsonNode;

public record NotificationEvent(
    String eventId,
    Long userId,
    String eventType,
    String title,
    String message,
    String referenceType,
    Long referenceId,
    String deduplicationKey) {
  public NotificationEvent(
      Long userId,
      String eventType,
      String title,
      String message,
      String referenceType,
      Long referenceId,
      String deduplicationKey) {
    this(null, userId, eventType, title, message, referenceType, referenceId, deduplicationKey);
  }

  public static NotificationEvent from(JsonNode payload) {
    String eventType = payload.path("eventType").asText();
    String eventId = payload.hasNonNull("eventId") ? payload.get("eventId").asText() : null;
    Long userId = payload.path("userId").asLong();
    Long orderId = payload.hasNonNull("orderId") ? payload.get("orderId").asLong() : null;
    Long referenceId =
        orderId != null
            ? orderId
            : payload.hasNonNull("paymentId") ? payload.get("paymentId").asLong() : null;
    String referenceType = orderId != null ? "ORDER" : "PAYMENT";
    if (eventType.isBlank() || userId <= 0 || referenceId == null || referenceId <= 0) {
      throw new IllegalArgumentException("Payload sự kiện thông báo không hợp lệ");
    }
    String title;
    String message;
    switch (eventType) {
      case "OrderCreated" -> {
        title = "Đơn hàng đã được tạo";
        message = "Đơn hàng #" + referenceId + " đã được tạo và đang chờ thanh toán.";
      }
      case "PaymentCompleted", "PaymentSucceeded" -> {
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
      case "OrderConfirmed" -> {
        title = "Đơn hàng đã được xác nhận";
        message = "Đơn hàng #" + referenceId + " đã được xác nhận sau khi thanh toán.";
      }
      case "OrderConfirmationRejected" -> {
        title = "Thanh toán cần được kiểm tra";
        message = "Thanh toán cho đơn hàng #" + referenceId + " chưa được chấp nhận và cần kiểm tra.";
      }
      case "OrderShipping" -> {
        title = "Đơn hàng đang được giao";
        message = "Đơn hàng #" + referenceId + " đang trên đường giao đến bạn.";
      }
      case "OrderCompleted" -> {
        title = "Đơn hàng đã hoàn tất";
        message = "Đơn hàng #" + referenceId + " đã được giao thành công.";
      }
      default -> throw new IllegalArgumentException("Loại sự kiện không được hỗ trợ");
    }
    return new NotificationEvent(
        eventId,
        userId,
        eventType,
        title,
        message,
        referenceType,
        referenceId,
        eventId == null || eventId.isBlank() ? eventType + ":" + referenceId : eventId);
  }
}
