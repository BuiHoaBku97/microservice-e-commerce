package com.ecommerce.payment.models.dtos.requests;

import jakarta.validation.constraints.NotNull;

public record SimulatePaymentRequest(@NotNull PaymentCallbackRequest.Result result) {}
