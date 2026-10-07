package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
    @NotBlank @Size(max = 50) String code,
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 180) String slug) {}
