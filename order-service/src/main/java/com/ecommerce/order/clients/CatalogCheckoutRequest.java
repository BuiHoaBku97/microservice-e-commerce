package com.ecommerce.order.clients;

import java.util.List;

public record CatalogCheckoutRequest(List<Long> skuIds) {}
