package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.ApiResponse;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;
import com.ecommerce.catalog.services.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService catalogService;

  public CatalogController(CatalogService catalogService) {
    this.catalogService = catalogService;
  }

  @GetMapping("/categories")
  public ApiResponse<List<CategoryResponse>> categories(HttpServletRequest request) {
    return ApiResponse.success(catalogService.listActiveCategories(), request.getRequestId());
  }

  @GetMapping("/products/search")
  public ApiResponse<List<ProductResponse>> search(
      @RequestParam(required = false) String q, HttpServletRequest request) {
    return ApiResponse.success(catalogService.searchPublicProducts(q), request.getRequestId());
  }

  @GetMapping("/products/{id}")
  public ApiResponse<ProductResponse> product(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(catalogService.getPublicProduct(id), request.getRequestId());
  }

  @PostMapping("/admin/categories")
  public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
      @Valid @RequestBody CreateCategoryRequest body, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(catalogService.createCategory(body), request.getRequestId()));
  }

  @PostMapping("/admin/products")
  public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
      @Valid @RequestBody CreateProductRequest body, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(catalogService.createProduct(body), request.getRequestId()));
  }

  @PostMapping("/admin/products/{productId}/skus")
  public ResponseEntity<ApiResponse<SkuResponse>> createSku(
      @PathVariable Long productId,
      @Valid @RequestBody CreateSkuRequest body,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(catalogService.createSku(productId, body), request.getRequestId()));
  }
}
