package com.ecommerce.notification.controllers;

import com.ecommerce.notification.models.entities.DeliveryAttempt;
import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.models.entities.NotificationStatus;
import com.ecommerce.notification.services.NotificationService;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/notifications")
public class AdminNotificationController {
  private final NotificationService notificationService;

  public AdminNotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public Page<AdminNotificationResponse> list(
      @RequestParam(required = false) NotificationStatus status,
      @RequestParam(required = false) Long orderId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return notificationService.listForAdmin(
            status, orderId, PageRequest.of(Math.max(0, page), Math.min(Math.max(size, 1), 100)))
        .map(AdminNotificationResponse::from);
  }

  @GetMapping("/{id}/attempts")
  public List<DeliveryAttemptResponse> attempts(@PathVariable Long id) {
    return notificationService.listAttempts(id).stream().map(DeliveryAttemptResponse::from).toList();
  }

  public record AdminNotificationResponse(
      Long id, Long userId, String eventType, Long referenceId, NotificationStatus status,
      int attempts, String lastError, Instant createdAt) {
    static AdminNotificationResponse from(Notification value) {
      return new AdminNotificationResponse(
          value.getId(), value.getUserId(), value.getEventType(), value.getReferenceId(),
          value.getStatus(), value.getAttempts(), value.getLastError(), value.getCreatedAt());
    }
  }

  public record DeliveryAttemptResponse(
      Long id, int attemptNumber, String outcome, String error, Instant attemptedAt) {
    static DeliveryAttemptResponse from(DeliveryAttempt value) {
      return new DeliveryAttemptResponse(
          value.getId(), value.getAttemptNumber(), value.getOutcome(), value.getError(),
          value.getAttemptedAt());
    }
  }
}
