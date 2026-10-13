package com.ecommerce.catalog.models.dtos.requests;

import com.ecommerce.catalog.models.entities.Status;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull Status status, @NotNull Long version) {}
