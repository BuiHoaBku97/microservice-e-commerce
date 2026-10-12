package com.ecommerce.identity.models.dtos.requests;

import jakarta.validation.constraints.NotNull;

public record CheckoutContextRequest(@NotNull Long addressId) {}
