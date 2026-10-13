package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateSkuRequest(
    @Positive Long price,
    @Size(max = 50) String color,
    @Size(max = 30) String size,
    @Size(max = 200) String reason,
    @NotNull Long version) {}
