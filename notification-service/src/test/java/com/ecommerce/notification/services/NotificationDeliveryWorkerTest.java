package com.ecommerce.notification.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notification.models.entities.DeliveryAttempt;
import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.models.entities.NotificationStatus;
import com.ecommerce.notification.repositories.DeliveryAttemptRepository;
import com.ecommerce.notification.repositories.NotificationRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryWorkerTest {
  @Mock NotificationRepository notifications;
  @Mock DeliveryAttemptRepository attempts;

  @Test
  void shouldMarkDueNotificationSentAndRecordAttempt() {
    Notification notification = new Notification(
        7L, "OrderConfirmed", "title", "message", "ORDER", 10L, "event-1");
    when(notifications
        .findTop50ByStatusAndNextAttemptAtLessThanEqualOrStatusAndNextAttemptAtIsNullOrderByCreatedAt(
            any(), any(Instant.class), any()))
        .thenReturn(List.of(notification));

    new NotificationDeliveryWorker(notifications, attempts, 3, 30).deliverPending();

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(notification.getAttempts()).isEqualTo(1);
    ArgumentCaptor<DeliveryAttempt> captor = ArgumentCaptor.forClass(DeliveryAttempt.class);
    verify(attempts).save(captor.capture());
    assertThat(captor.getValue().getOutcome()).isEqualTo("SENT");
    assertThat(captor.getValue().getAttemptNumber()).isEqualTo(1);
  }
}
