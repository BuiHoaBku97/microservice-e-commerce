package com.ecommerce.catalog.models.dtos.responses;

public record CheckoutItemResponse(
    Long skuId,
    Long productId,
    String skuCode,
    String productName,
    String color,
    String size,
    Long unitPrice,
    String currency,
    Long productVersion,
    boolean sellable) {}
