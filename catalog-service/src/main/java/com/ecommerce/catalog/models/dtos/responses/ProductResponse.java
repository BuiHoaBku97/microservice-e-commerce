package com.ecommerce.catalog.models.dtos.responses;

import com.ecommerce.catalog.models.entities.Status;

import java.util.List;

public record ProductResponse(Long id, String name, String description, Long categoryId,
                              List<String> imageUrls, Status status, Long version,
                              List<SkuResponse> skus) { }
