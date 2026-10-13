package com.ecommerce.notification.services;

import com.ecommerce.notification.models.entities.DeliveryAttempt;
import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.models.entities.NotificationStatus;
import com.ecommerce.notification.repositories.DeliveryAttemptRepository;
import com.ecommerce.notification.repositories.NotificationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationDeliveryWorker {
  private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryWorker.class);
  private final NotificationRepository notifications;
  private final DeliveryAttemptRepository attempts;
  private final int maxAttempts;
  private final long retrySeconds;

  public NotificationDeliveryWorker(NotificationRepository notifications,
      DeliveryAttemptRepository attempts,
      @Value("${app.notification.max-attempts:3}") int maxAttempts,
      @Value("${app.notification.retry-seconds:30}") long retrySeconds) {
    this.notifications = notifications; this.attempts = attempts;
    this.maxAttempts = maxAttempts; this.retrySeconds = retrySeconds;
  }

  @Scheduled(fixedDelayString = "${app.notification.delivery-delay-ms:5000}")
  @Transactional
  public void deliverPending() {
    var due = notifications
        .findTop50ByStatusAndNextAttemptAtLessThanEqualOrStatusAndNextAttemptAtIsNullOrderByCreatedAt(
            NotificationStatus.PENDING, Instant.now(), NotificationStatus.PENDING);
    due.forEach(this::deliver);
  }

  private void deliver(Notification notification) {
    try {
      log.info("notification delivery=DEMO_LOG id={} eventType={} referenceId={}",
          notification.getId(), notification.getEventType(), notification.getReferenceId());
      notification.markSent();
      attempts.save(new DeliveryAttempt(notification, notification.getAttempts(), "SENT", null));
    } catch (RuntimeException exception) {
      notification.markFailed(exception.getMessage(), Instant.now().plusSeconds(retrySeconds), maxAttempts);
      attempts.save(new DeliveryAttempt(notification, notification.getAttempts(), "FAILED", exception.getMessage()));
    }
  }
}
