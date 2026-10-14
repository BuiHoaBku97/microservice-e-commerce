package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Product;
import com.ecommerce.catalog.models.entities.Status;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByStatusAndNameContainingIgnoreCaseOrderByNameAsc(Status status, String name);

  List<Product> findByStatusOrderByNameAsc(Status status);

  List<Product> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);
}
