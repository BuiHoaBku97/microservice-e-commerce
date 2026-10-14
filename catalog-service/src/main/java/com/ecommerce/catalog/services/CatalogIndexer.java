package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.entities.*;
import com.ecommerce.catalog.repositories.*;
import com.ecommerce.catalog.search.CatalogProductDocument;
import java.text.Normalizer;
import java.util.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CatalogIndexer {
  private final CatalogOutboxRepository catalogOutboxRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final CatalogReindexJobRepository catalogReindexJobRepository;
  private final CatalogIndexManager indexManager;

  public CatalogIndexer(
      CatalogOutboxRepository catalogOutboxRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      CatalogReindexJobRepository catalogReindexJobRepository,
      CatalogIndexManager indexManager) {
    this.catalogOutboxRepository = catalogOutboxRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.catalogReindexJobRepository = catalogReindexJobRepository;
    this.indexManager = indexManager;
  }

  @Scheduled(fixedDelayString = "${app.catalog.index-delay-ms:3000}")
  @Transactional
  public void indexPending() {
    indexManager.ensureServingIndex();
    for (CatalogOutboxEvent event : catalogOutboxRepository.claimBatch())
      try {
        index(event);
        event.complete();
      } catch (Exception exception) {
        event.fail(exception.getMessage());
      }
  }

  public void index(CatalogOutboxEvent event) {
    Product product = productRepository.findById(event.getProductId()).orElse(null);
    List<String> targetIndices = new ArrayList<>();
    targetIndices.add(CatalogIndexManager.ALIAS);
    catalogReindexJobRepository
        .findFirstByStatusInOrderByCreatedAtAsc(
            List.of(CatalogReindexJob.Status.PENDING, CatalogReindexJob.Status.RUNNING))
        .map(CatalogReindexJob::getTargetIndex)
        .ifPresent(targetIndices::add);
    for (String targetIndex : targetIndices) {
      if (product == null || product.getStatus() != Status.ACTIVE)
        indexManager.delete(event.getProductId(), targetIndex);
      else
        indexManager.saveIfNewer(
            document(
                product,
                skuRepository.findByProductIdAndStatusOrderByIdAsc(product.getId(), Status.ACTIVE)),
            targetIndex);
    }
  }

  public CatalogProductDocument document(Product product, List<Sku> skuRows) {
    long minimumPrice = skuRows.stream().mapToLong(Sku::getPrice).min().orElse(0);
    String currency = skuRows.isEmpty() ? "VND" : skuRows.getFirst().getCurrency();
    return new CatalogProductDocument(
        product.getId(),
        product.getName(),
        normalize(product.getName()),
        product.getDescription(),
        product.getCategory().getId(),
        product.getImageUrls().isEmpty() ? null : product.getImageUrls().getFirst(),
        skuRows.stream().map(Sku::getColor).filter(color -> !color.isBlank()).distinct().toList(),
        skuRows.stream().map(Sku::getSize).filter(size -> !size.isBlank()).distinct().toList(),
        minimumPrice,
        currency,
        product.getSearchVersion(),
        product.getStatus().name(),
        product.getCreatedAt());
  }

  private String normalize(String value) {
    return value == null
        ? ""
        : Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT);
  }
}
