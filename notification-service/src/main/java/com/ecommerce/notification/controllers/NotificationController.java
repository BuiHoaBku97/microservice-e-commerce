package com.ecommerce.notification.controllers;

import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.models.dtos.ApiResponse;
import com.ecommerce.notification.models.dtos.PageResponse;
import com.ecommerce.notification.services.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.RequestParam;
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
  public ApiResponse<PageResponse<NotificationResponse>> list(
      Authentication authentication,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    var notifications = notificationService.listByUser(
        userId(authentication),
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))
        .map(NotificationResponse::from);
    return ApiResponse.success(PageResponse.from(notifications), request.getRequestId());
  }

  @PatchMapping("/{notificationId}/read")
  public ApiResponse<NotificationResponse> markRead(
      Authentication authentication, @PathVariable Long notificationId,
      HttpServletRequest request) {
    return ApiResponse.success(NotificationResponse.from(
        notificationService.markRead(userId(authentication), notificationId)), request.getRequestId());
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
