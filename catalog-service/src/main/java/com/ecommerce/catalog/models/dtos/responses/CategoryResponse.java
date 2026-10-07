package com.ecommerce.catalog.models.dtos.responses;

import com.ecommerce.catalog.models.entities.Status;

public record CategoryResponse(
    Long id, String code, String name, String slug, Status status, Long version) {}
