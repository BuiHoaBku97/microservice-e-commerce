package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Status;
import com.ecommerce.catalog.models.entities.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByStatusAndNameContainingIgnoreCaseOrderByNameAsc(Status status, String name);
    List<Product> findByStatusOrderByNameAsc(Status status);
}
