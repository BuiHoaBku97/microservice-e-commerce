package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Category;
import com.ecommerce.catalog.models.entities.Status;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByCodeIgnoreCase(String code);
    boolean existsBySlugIgnoreCase(String slug);
    List<Category> findByStatusOrderByNameAsc(Status status);
}
