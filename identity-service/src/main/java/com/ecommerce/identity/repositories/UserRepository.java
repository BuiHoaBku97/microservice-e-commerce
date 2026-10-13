package com.ecommerce.identity.repositories;

import com.ecommerce.identity.models.entities.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.ecommerce.identity.models.entities.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {
  Optional<User> findByEmail(String email);

  boolean existsByEmail(String email);

  @Query("""
      select user from User user
      where (:status is null or user.status = :status)
        and (:keyword is null or lower(user.email) like lower(concat('%', :keyword, '%'))
          or lower(user.fullName) like lower(concat('%', :keyword, '%')))
      order by user.createdAt desc
      """)
  Page<User> findForAdmin(
      @Param("status") UserStatus status,
      @Param("keyword") String keyword,
      Pageable pageable);
}
