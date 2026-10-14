package com.ecommerce.catalog.models.dtos.responses;

public record SearchProductResponse(
    Long id,
    String name,
    Long categoryId,
    String imageUrl,
    Long minPrice,
    String currency,
    Long productVersion) {}
