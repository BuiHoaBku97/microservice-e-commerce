package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.Product;
import com.ecommerce.catalog.models.entities.Status;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
  List<Product> findByStatusAndNameContainingIgnoreCaseOrderByNameAsc(Status status, String name);

  List<Product> findByStatusOrderByNameAsc(Status status);

  List<Product> findByIdGreaterThanOrderByIdAsc(Long id, Pageable pageable);

  @Query("""
      select product from Product product
      where (:status is null or product.status = :status)
        and (:categoryId is null or product.category.id = :categoryId)
      order by product.id desc
      """)
  Page<Product> findForAdmin(
      @Param("status") Status status, @Param("categoryId") Long categoryId, Pageable pageable);
}
