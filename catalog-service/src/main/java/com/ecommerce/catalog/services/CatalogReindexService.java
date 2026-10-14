package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.entities.*;
import com.ecommerce.catalog.repositories.*;
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
  private final CatalogIndexManager indexManager;
  private final CatalogIndexer catalogIndexer;

  public CatalogReindexService(
      CatalogReindexJobRepository catalogReindexJobRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      CatalogIndexManager indexManager,
      CatalogIndexer catalogIndexer) {
    this.catalogReindexJobRepository = catalogReindexJobRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.indexManager = indexManager;
    this.catalogIndexer = catalogIndexer;
  }

  @Transactional
  public CatalogReindexJob request(Long requestedByUserId) {
    try {
      return catalogReindexJobRepository.saveAndFlush(
          new CatalogReindexJob(requestedByUserId, productRepository.count()));
    } catch (DataIntegrityViolationException exception) {
      throw new IllegalStateException("REINDEX_ALREADY_RUNNING", exception);
    }
  }

  @Transactional(readOnly = true)
  public CatalogReindexJob get(Long id) {
    return catalogReindexJobRepository
        .findById(id)
        .orElseThrow(() -> new IllegalArgumentException("REINDEX_JOB_NOT_FOUND"));
  }

  @Scheduled(fixedDelayString = "${app.catalog.reindex-delay-ms:2000}")
  @Transactional
  public void run() {
    catalogReindexJobRepository
        .findFirstByStatusInOrderByCreatedAtAsc(
            List.of(CatalogReindexJob.Status.PENDING, CatalogReindexJob.Status.RUNNING))
        .ifPresent(this::process);
  }

  private void process(CatalogReindexJob job) {
    try {
      job.start();
      indexManager.createIndex(job.getTargetIndex());
      List<Product> productBatch =
          productRepository.findByIdGreaterThanOrderByIdAsc(
              job.getCheckpoint(), PageRequest.of(0, 100));
      for (Product product : productBatch) {
        try {
          if (product.getStatus() == Status.ACTIVE)
            indexManager.saveIfNewer(
                catalogIndexer.document(
                    product,
                    skuRepository.findByProductIdAndStatusOrderByIdAsc(
                        product.getId(), Status.ACTIVE)),
                job.getTargetIndex());
          else {
            indexManager.delete(product.getId(), job.getTargetIndex());
          }
          job.progress(product.getId());
        } catch (Exception exception) {
          job.failItem(exception.getMessage());
        }
      }
      if (productBatch.size() < 100) {
        if (job.getFailedCount() > 0) {
          job.complete();
        } else {
          indexManager.switchAlias(job.getAliasName(), job.getTargetIndex());
          job.complete();
        }
      }
    } catch (Exception exception) {
      job.fail(exception.getMessage());
    }
  }
}
