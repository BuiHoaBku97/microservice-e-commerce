package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSkuRequest(@NotBlank @Size(max = 80) String code,
                               @Size(max = 50) String color,
                               @Size(max = 30) String size,
                               @NotNull @Positive Long price) { }
