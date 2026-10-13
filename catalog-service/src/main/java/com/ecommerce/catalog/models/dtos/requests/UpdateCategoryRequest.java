package com.ecommerce.catalog.models.dtos.requests;

import com.ecommerce.catalog.models.entities.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
    @NotBlank @Size(max = 150) String name,
    @NotBlank @Size(max = 180) String slug,
    @NotNull Status status,
    @NotNull Long version) {}
