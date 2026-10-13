package com.ecommerce.catalog.models.dtos.responses;

import com.ecommerce.catalog.models.entities.SkuPriceHistory;
import java.time.Instant;

public record SkuPriceHistoryResponse(
    Long id, Long skuId, Long oldPrice, Long newPrice, String reason, Instant createdAt) {
  public static SkuPriceHistoryResponse from(SkuPriceHistory history) {
    return new SkuPriceHistoryResponse(
        history.getId(), history.getSku().getId(), history.getOldPrice(), history.getNewPrice(),
        history.getReason(), history.getCreatedAt());
  }
}
