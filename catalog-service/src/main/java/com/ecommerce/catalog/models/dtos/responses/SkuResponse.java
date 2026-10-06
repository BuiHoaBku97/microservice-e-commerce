package com.ecommerce.catalog.models.dtos.responses;

import com.ecommerce.catalog.models.entities.CatalogStatus;

public record SkuResponse(Long id, Long productId, String code, String color, String size,
                          Long price, String currency, CatalogStatus status, Long version) { }
