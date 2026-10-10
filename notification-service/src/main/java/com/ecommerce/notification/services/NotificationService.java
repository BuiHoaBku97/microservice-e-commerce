package com.ecommerce.notification.services;

import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.repositories.NotificationRepository;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {
  private final NotificationRepository notificationRepository;

  public NotificationService(NotificationRepository notificationRepository) {
    this.notificationRepository = notificationRepository;
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
              notification.markSent();
              return notificationRepository.save(notification);
            });
  }

  @Transactional
  public List<Notification> listByUser(Long userId) {
    return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
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
}
