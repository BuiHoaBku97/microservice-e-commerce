package com.ecommerce.identity.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
    @NotBlank @Size(max = 150) String fullName,
    @Size(max = 30) String phone,
    @NotNull Long version) {}
