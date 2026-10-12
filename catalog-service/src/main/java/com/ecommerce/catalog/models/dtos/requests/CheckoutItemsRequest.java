package com.ecommerce.catalog.models.dtos.requests;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CheckoutItemsRequest(@NotEmpty @Size(max = 100) List<@NotNull Long> skuIds) {}
