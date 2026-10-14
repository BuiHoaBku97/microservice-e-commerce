package com.ecommerce.catalog.repositories;

import com.ecommerce.catalog.models.entities.CatalogReindexJob;
import java.util.*;
import org.springframework.data.jpa.repository.*;

public interface CatalogReindexJobRepository extends JpaRepository<CatalogReindexJob, Long> {
  Optional<CatalogReindexJob> findFirstByStatusInOrderByCreatedAtAsc(
      Collection<CatalogReindexJob.Status> statuses);
}
