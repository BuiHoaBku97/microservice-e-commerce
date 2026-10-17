package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.OrderPaymentContext;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderPaymentContextRepository
    extends JpaRepository<OrderPaymentContext, Long> {
  @Modifying(flushAutomatically = true)
  @Query(
      value =
          "insert into order_payment_contexts(order_id) values (:orderId) on conflict (order_id) do nothing",
      nativeQuery = true)
  int ensureExists(@Param("orderId") Long orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select context from OrderPaymentContext context where context.orderId = :orderId")
  Optional<OrderPaymentContext> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
