package com.ecommerce.notification.services;

import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.repositories.NotificationRepository;
import com.ecommerce.notification.repositories.DeliveryAttemptRepository;
import com.ecommerce.notification.models.entities.DeliveryAttempt;
import com.ecommerce.notification.models.entities.NotificationStatus;
import com.ecommerce.notification.messaging.events.NotificationEvent;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class NotificationService {
  private final NotificationRepository notificationRepository;
  private final DeliveryAttemptRepository deliveryAttemptRepository;

  public NotificationService(
      NotificationRepository notificationRepository,
      DeliveryAttemptRepository deliveryAttemptRepository) {
    this.notificationRepository = notificationRepository;
    this.deliveryAttemptRepository = deliveryAttemptRepository;
  }

  @Transactional
  public Notification createFromEvent(NotificationEvent event) {
    return notificationRepository
        .findByDeduplicationKey(event.deduplicationKey())
        .orElseGet(
            () -> {
              Notification notification =
                  new Notification(
                      event.userId(),
                      event.eventType(),
                      event.title(),
                      event.message(),
                      event.referenceType(),
                      event.referenceId(),
                      event.deduplicationKey());
              return notificationRepository.save(notification);
            });
  }

  @Transactional
  public Page<Notification> listByUser(Long userId, Pageable pageable) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
  }

  @Transactional
  public Notification markRead(Long userId, Long notificationId) {
    Notification notification =
        notificationRepository
            .findByIdAndUserId(notificationId, userId)
            .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy thông báo"));
    notification.markRead();
    return notificationRepository.save(notification);
  }

  @Transactional
  public Page<Notification> listForAdmin(
      NotificationStatus status, Long orderId, Pageable pageable) {
    return notificationRepository.findForAdmin(status, orderId, pageable);
  }

  @Transactional
  public List<DeliveryAttempt> listAttempts(Long notificationId) {
    if (!notificationRepository.existsById(notificationId)) {
      throw new IllegalArgumentException("NOTIFICATION_NOT_FOUND");
    }
    return deliveryAttemptRepository.findByNotificationIdOrderByAttemptedAtDesc(notificationId);
  }
}
