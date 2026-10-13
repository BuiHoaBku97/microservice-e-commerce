package com.ecommerce.notification.repositories;
import com.ecommerce.notification.models.entities.DeliveryAttempt;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, Long> {
  List<DeliveryAttempt> findByNotificationIdOrderByAttemptedAtDesc(Long notificationId);
}
