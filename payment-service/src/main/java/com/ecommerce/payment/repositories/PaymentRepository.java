package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import com.ecommerce.payment.models.entities.PaymentStatus;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByOrderId(Long orderId);

  Optional<Payment> findByOrderIdAndUserId(Long orderId, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from Payment payment where payment.orderId = :orderId and payment.userId = :userId")
  Optional<Payment> findByOrderIdAndUserIdForUpdate(@Param("orderId") Long orderId,
      @Param("userId") Long userId);

  boolean existsByOrderId(Long orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from Payment payment where payment.id = :id")
  Optional<Payment> findByIdForUpdate(@Param("id") Long id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select payment from Payment payment where payment.orderId = :orderId")
  Optional<Payment> findByOrderIdForUpdate(@Param("orderId") Long orderId);

  @Query("""
      select payment from Payment payment
      where (:status is null or payment.status = :status)
        and (:orderId is null or payment.orderId = :orderId)
        and (:userId is null or payment.userId = :userId)
        and (:from is null or payment.createdAt >= :from)
        and (:to is null or payment.createdAt < :to)
      order by payment.createdAt desc
      """)
  Page<Payment> findForAdmin(
      @Param("status") PaymentStatus status,
      @Param("orderId") Long orderId,
      @Param("userId") Long userId,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  @Query(value = "select coalesce(sum(amount), 0) from payments where funds_status = 'RECEIVED'", nativeQuery = true)
  Long sumReceivedAmount();

  @Query(value = "select coalesce(sum(amount), 0) from payments where order_acceptance = 'ACCEPTED'", nativeQuery = true)
  Long sumAcceptedAmount();

  @Query(value = "select coalesce(sum(amount), 0) from payments where status = 'REQUIRES_REVIEW'", nativeQuery = true)
  Long sumRequiresReviewAmount();
}
