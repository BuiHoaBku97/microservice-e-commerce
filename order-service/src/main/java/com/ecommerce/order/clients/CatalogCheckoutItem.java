package com.ecommerce.order.clients;

public record CatalogCheckoutItem(
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
