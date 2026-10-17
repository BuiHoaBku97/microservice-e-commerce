package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  List<Order> findByUserIdOrderByCreatedAtDesc(Long userId);

  @EntityGraph(attributePaths = "items")
  Optional<Order> findByIdAndUserId(Long id, Long userId);

  @EntityGraph(attributePaths = "items")
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findWithItemsById(@Param("id") Long id);

  Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id = :id")
  Optional<Order> findByIdForUpdate(@Param("id") Long id);

  @Query(
      value =
          "SELECT * FROM orders WHERE status = 'PENDING_PAYMENT'"
              + " AND payment_due_at <= clock_timestamp() ORDER BY payment_due_at",
      nativeQuery = true)
  List<Order> findExpiredPendingOrders();

  @Query(
      """
      select o from Order o
      where (:userId is null or o.userId = :userId)
        and (:status is null or o.status = :status)
        and (:from is null or o.createdAt >= :from)
        and (:to is null or o.createdAt < :to)
      order by o.createdAt desc
      """)
  Page<Order> findForAdmin(
      @Param("userId") Long userId,
      @Param("status") OrderStatus status,
      @Param("from") Instant from,
      @Param("to") Instant to,
      Pageable pageable);

  @Query(
      "select o.status, count(o) from Order o where (:from is null or o.createdAt >= :from) and"
          + " (:to is null or o.createdAt < :to) group by o.status")
  List<Object[]> countByStatus(@Param("from") Instant from, @Param("to") Instant to);

  @Query(
      "select coalesce(sum(o.totalAmount), 0) from Order o where o.status ="
          + " com.ecommerce.order.models.entities.OrderStatus.COMPLETED and (:from is null or"
          + " o.completedAt >= :from) and (:to is null or o.completedAt < :to)")
  Long completedOrderValue(@Param("from") Instant from, @Param("to") Instant to);
}
