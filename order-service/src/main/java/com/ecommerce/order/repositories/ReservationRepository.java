package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Reservation;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
  Optional<Reservation> findByOrderId(Long orderId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select distinct r from Reservation r left join fetch r.items where r.orderId = :orderId")
  Optional<Reservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);
}
