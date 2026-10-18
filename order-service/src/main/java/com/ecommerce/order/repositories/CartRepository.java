package com.ecommerce.order.repositories;

import com.ecommerce.order.models.entities.Cart;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CartRepository extends JpaRepository<Cart, Long> {
  @Query("select distinct c from Cart c left join fetch c.items where c.userId = :userId")
  Optional<Cart> findByUserId(@Param("userId") Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select distinct c from Cart c left join fetch c.items where c.userId = :userId")
  Optional<Cart> findByUserIdForUpdate(@Param("userId") Long userId);
}
