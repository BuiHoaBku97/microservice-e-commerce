package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.entities.*;
import com.ecommerce.catalog.repositories.*;
import com.ecommerce.catalog.search.CatalogProductSearchRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogReindexService {
  private final CatalogReindexJobRepository catalogReindexJobRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final CatalogProductSearchRepository productSearchRepository;
  private final CatalogIndexer indexer;

  public CatalogReindexService(
      CatalogReindexJobRepository catalogReindexJobRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      CatalogProductSearchRepository productSearchRepository,
      CatalogIndexer indexer) {
    this.catalogReindexJobRepository = catalogReindexJobRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.productSearchRepository = productSearchRepository;
    this.indexer = indexer;
  }

  @Transactional
  public CatalogReindexJob request(Long requestedByUserId) {
    try {
      return catalogReindexJobRepository.saveAndFlush(new CatalogReindexJob(requestedByUserId, productRepository.count()));
    } catch (DataIntegrityViolationException ex) {
      throw new IllegalStateException("REINDEX_ALREADY_RUNNING", ex);
    }
  }

  @Transactional(readOnly = true)
  public CatalogReindexJob get(Long id) {
    return catalogReindexJobRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("REINDEX_JOB_NOT_FOUND"));
  }

  @Scheduled(fixedDelayString = "${app.catalog.reindex-delay-ms:2000}")
  @Transactional
  public void run() {
    catalogReindexJobRepository.findFirstByStatusInOrderByCreatedAtAsc(
            List.of(CatalogReindexJob.Status.PENDING, CatalogReindexJob.Status.RUNNING))
        .ifPresent(this::process);
  }

  private void process(CatalogReindexJob job) {
    try {
      job.start();
      List<Product> batch =
          productRepository.findByIdGreaterThanOrderByIdAsc(job.getCheckpoint(), PageRequest.of(0, 100));
      for (Product product : batch) {
        try {
          if (product.getStatus() == Status.ACTIVE)
            productSearchRepository.save(
                indexer.document(
                    product,
                    skuRepository.findByProductIdAndStatusOrderByIdAsc(product.getId(), Status.ACTIVE)));
          else productSearchRepository.deleteById(product.getId());
          job.progress(product.getId());
        } catch (Exception ex) {
          job.failItem(ex.getMessage());
        }
      }
      if (batch.size() < 100) job.complete();
    } catch (Exception ex) {
      job.fail(ex.getMessage());
    }
  }
}
