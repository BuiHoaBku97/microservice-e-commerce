package com.ecommerce.notification.controllers;

import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.services.NotificationService;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationController {
  private final NotificationService notificationService;

  public NotificationController(NotificationService notificationService) {
    this.notificationService = notificationService;
  }

  @GetMapping
  public List<NotificationResponse> list(Authentication authentication) {
    return notificationService.listByUser(userId(authentication)).stream()
        .map(NotificationResponse::from)
        .toList();
  }

  @PatchMapping("/{notificationId}/read")
  public NotificationResponse markRead(
      Authentication authentication, @PathVariable Long notificationId) {
    return NotificationResponse.from(
        notificationService.markRead(userId(authentication), notificationId));
  }

  private Long userId(Authentication authentication) {
    return Long.valueOf(authentication.getName());
  }

  public record NotificationResponse(
      Long id,
      String eventType,
      String title,
      String message,
      String referenceType,
      Long referenceId,
      String status,
      boolean read,
      Instant createdAt) {
    static NotificationResponse from(Notification notification) {
      return new NotificationResponse(
          notification.getId(),
          notification.getEventType(),
          notification.getTitle(),
          notification.getMessage(),
          notification.getReferenceType(),
          notification.getReferenceId(),
          notification.getStatus().name(),
          notification.getReadAt() != null,
          notification.getCreatedAt());
    }
  }
}
