package com.ecommerce.identity.models.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
    @NotBlank @Size(max = 150) String recipientName,
    @NotBlank @Size(max = 30) String recipientPhone,
    @NotBlank @Size(max = 255) String addressLine,
    @NotBlank @Size(max = 100) String ward,
    @Size(max = 100) String district,
    @NotBlank @Size(max = 100) String province,
    boolean defaultAddress) {}
