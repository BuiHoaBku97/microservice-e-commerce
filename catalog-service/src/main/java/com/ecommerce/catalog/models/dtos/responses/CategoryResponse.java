package com.ecommerce.catalog.models.dtos.responses;

import com.ecommerce.catalog.models.entities.CatalogStatus;

public record CategoryResponse(Long id, String code, String name, String slug, CatalogStatus status, Long version) { }
