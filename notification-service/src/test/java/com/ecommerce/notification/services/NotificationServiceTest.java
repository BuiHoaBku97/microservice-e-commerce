package com.ecommerce.notification.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.models.entities.NotificationStatus;
import com.ecommerce.notification.messaging.events.NotificationEvent;
import com.ecommerce.notification.repositories.NotificationRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
  @Mock private NotificationRepository notificationRepository;

  @InjectMocks private NotificationService notificationService;

  @Test
  void shouldCreateSentNotification() {
    NotificationEvent event =
        new NotificationEvent(
            7L, "PaymentCompleted", "Thanh toán thành công", "Đơn hàng đã thanh toán", "ORDER", 10L,
            "PaymentCompleted:10");
    when(notificationRepository.findByDeduplicationKey("PaymentCompleted:10"))
        .thenReturn(Optional.empty());
    when(notificationRepository.save(any(Notification.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    Notification notification = notificationService.createFromEvent(event);

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(notification.getUserId()).isEqualTo(7L);
    verify(notificationRepository).save(any(Notification.class));
  }

  @Test
  void shouldReturnExistingNotificationWhenEventIsDuplicated() {
    Notification existing =
        new Notification(
            7L,
            "PaymentCompleted",
            "Thanh toán thành công",
            "Đơn hàng đã thanh toán",
            "ORDER",
            10L,
            "PaymentCompleted:10");
    when(notificationRepository.findByDeduplicationKey("PaymentCompleted:10"))
        .thenReturn(Optional.of(existing));

    Notification notification =
        notificationService.createFromEvent(
            new NotificationEvent(
                7L,
                "PaymentCompleted",
                "Thanh toán thành công",
                "Đơn hàng đã thanh toán",
                "ORDER",
                10L,
                "PaymentCompleted:10"));

    assertThat(notification).isSameAs(existing);
    verify(notificationRepository, never()).save(any(Notification.class));
  }

  @Test
  void shouldNotMarkAnotherUsersNotificationAsRead() {
    when(notificationRepository.findByIdAndUserId(1L, 99L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> notificationService.markRead(99L, 1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Không tìm thấy thông báo");
  }
}
