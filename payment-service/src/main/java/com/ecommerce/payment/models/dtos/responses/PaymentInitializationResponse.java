package com.ecommerce.payment.models.dtos.responses;

public record PaymentInitializationResponse(
    Long paymentId, Long orderId, String paymentMethod, String nextAction, String paymentUrl) {}
