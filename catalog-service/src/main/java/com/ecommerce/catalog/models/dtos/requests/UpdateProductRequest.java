package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateProductRequest(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 200) String name,
    String description,
    List<@Size(max = 2048) String> imageUrls,
    @NotNull Long version) {}
