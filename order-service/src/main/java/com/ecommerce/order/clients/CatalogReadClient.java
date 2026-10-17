package com.ecommerce.order.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "catalog-service",
    contextId = "catalogReadClient",
    configuration = CatalogReadClientConfiguration.class)
public interface CatalogReadClient {
  @GetMapping("/internal/v1/skus/{skuId}")
  CatalogCheckoutItem getSku(@PathVariable Long skuId);
}
