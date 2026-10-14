package com.ecommerce.identity.repositories;

import com.ecommerce.identity.models.entities.AccountStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountStatusHistoryRepository extends JpaRepository<AccountStatusHistory, Long> {
  List<AccountStatusHistory> findByUserIdOrderByCreatedAtDesc(Long userId);
}
