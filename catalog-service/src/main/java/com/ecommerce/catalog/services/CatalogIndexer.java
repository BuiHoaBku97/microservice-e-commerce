package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.entities.*;
import com.ecommerce.catalog.repositories.*;
import com.ecommerce.catalog.search.*;
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
  private final CatalogProductSearchRepository productSearchRepository;

  public CatalogIndexer(
      CatalogOutboxRepository catalogOutboxRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      CatalogProductSearchRepository productSearchRepository) {
    this.catalogOutboxRepository = catalogOutboxRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.productSearchRepository = productSearchRepository;
  }

  @Scheduled(fixedDelayString = "${app.catalog.index-delay-ms:3000}")
  @Transactional
  public void indexPending() {
    for (CatalogOutboxEvent event : catalogOutboxRepository.claimBatch())
      try {
        index(event);
        event.complete();
      } catch (Exception ex) {
        event.fail(ex.getMessage());
      }
  }

  public void index(CatalogOutboxEvent event) {
    Product product = productRepository.findById(event.getProductId()).orElse(null);
    if (product == null || product.getStatus() != Status.ACTIVE) {
      productSearchRepository.deleteById(event.getProductId());
      return;
    }
    CatalogProductDocument current = productSearchRepository.findById(product.getId()).orElse(null);
    if (current != null && current.getProductVersion() > event.getProductVersion()) return;
    productSearchRepository.save(
        document(
            product, skuRepository.findByProductIdAndStatusOrderByIdAsc(product.getId(), Status.ACTIVE)));
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
        skuRows.stream().map(Sku::getColor).filter(v -> !v.isBlank()).distinct().toList(),
        skuRows.stream().map(Sku::getSize).filter(v -> !v.isBlank()).distinct().toList(),
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
            .replaceAll("\\product{M}+", "")
            .replace('đ', 'd')
            .replace('Đ', 'D')
            .toLowerCase(Locale.ROOT);
  }
}
