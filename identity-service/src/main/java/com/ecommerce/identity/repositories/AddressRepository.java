package com.ecommerce.identity.repositories;

import com.ecommerce.identity.models.entities.Address;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AddressRepository extends JpaRepository<Address, Long> {
  List<Address> findByUserIdOrderByDefaultAddressDescIdAsc(Long userId);

  Optional<Address> findByIdAndUserId(Long id, Long userId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Address a where a.userId = :userId order by a.id")
  List<Address> findByUserIdForUpdate(@Param("userId") Long userId);
}
