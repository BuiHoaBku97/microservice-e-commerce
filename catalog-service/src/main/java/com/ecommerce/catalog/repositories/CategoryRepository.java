package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Category;
import com.ecommerce.catalog.models.entities.Status;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
  boolean existsByCodeIgnoreCase(String code);

  boolean existsBySlugIgnoreCase(String slug);

  Page<Category> findByStatus(Status status, Pageable pageable);
}
