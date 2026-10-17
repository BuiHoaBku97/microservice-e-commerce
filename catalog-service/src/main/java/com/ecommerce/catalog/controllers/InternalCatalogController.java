package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.dtos.requests.CheckoutItemsRequest;
import com.ecommerce.catalog.models.dtos.responses.CheckoutItemResponse;
import com.ecommerce.catalog.services.CatalogService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/skus")
public class InternalCatalogController {
  private final CatalogService catalogService;

  public InternalCatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @PostMapping("/checkout-items")
  public List<CheckoutItemResponse> checkoutItems(
      @Valid @RequestBody CheckoutItemsRequest request) {
    return catalogService.getCheckoutItems(request.skuIds());
  }

  @GetMapping("/{skuId}")
  public CheckoutItemResponse sku(@PathVariable Long skuId) {
    return catalogService.getSkuForInventory(skuId);
  }
}
