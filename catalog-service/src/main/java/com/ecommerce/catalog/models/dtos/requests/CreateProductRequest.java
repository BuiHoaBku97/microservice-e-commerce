package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateProductRequest(
    @NotNull Long categoryId,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 5000) String description,
    @Size(max = 20) List<@NotBlank @Size(max = 2048) String> imageUrls) {}
