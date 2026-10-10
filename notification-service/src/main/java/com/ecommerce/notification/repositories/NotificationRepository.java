package com.ecommerce.notification.repositories;

import com.ecommerce.notification.models.entities.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);

  Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
