package com.ecommerce.identity.models.dtos.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChangeUserStatusRequest(
    @Size(max = 200) String reason,
    @NotNull Long version) {}
