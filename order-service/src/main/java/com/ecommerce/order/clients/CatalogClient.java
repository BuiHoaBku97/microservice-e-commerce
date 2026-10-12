package com.ecommerce.order.clients;

import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "catalog-service", configuration = CatalogClientConfiguration.class)
public interface CatalogClient {
  @PostMapping("/internal/v1/skus/checkout-items")
  List<CatalogCheckoutItem> getCheckoutItems(
      @RequestBody CatalogCheckoutRequest request,
      @RequestHeader("Authorization") String authorization);
}
