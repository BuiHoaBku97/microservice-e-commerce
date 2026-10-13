package com.ecommerce.notification.repositories;

import com.ecommerce.notification.models.entities.Notification;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
  List<Notification> findByUserIdOrderByCreatedAtDesc(Long userId);

  Optional<Notification> findByDeduplicationKey(String deduplicationKey);

  Optional<Notification> findByIdAndUserId(Long id, Long userId);

  List<Notification> findTop50ByStatusAndNextAttemptAtLessThanEqualOrStatusAndNextAttemptAtIsNullOrderByCreatedAt(
      com.ecommerce.notification.models.entities.NotificationStatus retryStatus, Instant now,
      com.ecommerce.notification.models.entities.NotificationStatus pendingStatus);

  @Query("""
      select notification from Notification notification
      where (:status is null or notification.status = :status)
        and (:orderId is null or (notification.referenceType = 'ORDER' and notification.referenceId = :orderId))
      order by notification.createdAt desc
      """)
  Page<Notification> findForAdmin(
      @Param("status") com.ecommerce.notification.models.entities.NotificationStatus status,
      @Param("orderId") Long orderId,
      Pageable pageable);
}
