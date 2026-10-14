package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.entities.*;
import com.ecommerce.catalog.repositories.CatalogOutboxRepository;
import org.springframework.stereotype.Service;

@Service
public class CatalogIndexEventService {
  private final CatalogOutboxRepository catalogOutboxRepository;

  public CatalogIndexEventService(CatalogOutboxRepository catalogOutboxRepository) {
    this.catalogOutboxRepository = catalogOutboxRepository;
  }

  public void enqueue(Product product, String eventType) {
    catalogOutboxRepository.save(new CatalogOutboxEvent(product, eventType));
  }
}
