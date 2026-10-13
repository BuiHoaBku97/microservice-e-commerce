package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.requests.ChangeStatusRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.ApiResponse;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuPriceHistoryResponse;
import com.ecommerce.catalog.services.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

  @GetMapping("/admin/categories")
  public ApiResponse<List<CategoryResponse>> adminCategories(HttpServletRequest request) {
    return ApiResponse.success(catalogService.listAllCategories(), request.getRequestId());
  }

  @PatchMapping("/admin/categories/{id}")
  public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id,
      @Valid @RequestBody UpdateCategoryRequest body, HttpServletRequest request) {
    return ApiResponse.success(catalogService.updateCategory(id, body), request.getRequestId());
  }

  @GetMapping("/admin/products")
  public ApiResponse<List<ProductResponse>> adminProducts(HttpServletRequest request) {
    return ApiResponse.success(catalogService.listAllProducts(), request.getRequestId());
  }

  @GetMapping("/admin/products/{id}")
  public ApiResponse<ProductResponse> adminProduct(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(catalogService.getAdminProduct(id), request.getRequestId());
  }

  @PatchMapping("/admin/products/{id}")
  public ApiResponse<ProductResponse> updateProduct(@PathVariable Long id,
      @Valid @RequestBody UpdateProductRequest body, HttpServletRequest request) {
    return ApiResponse.success(catalogService.updateProduct(id, body), request.getRequestId());
  }

  @PutMapping("/admin/products/{id}/status")
  public ApiResponse<ProductResponse> productStatus(@PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest body, HttpServletRequest request) {
    return ApiResponse.success(catalogService.changeProductStatus(id, body), request.getRequestId());
  }

  @PatchMapping("/admin/skus/{id}")
  public ApiResponse<SkuResponse> updateSku(@PathVariable Long id,
      @Valid @RequestBody UpdateSkuRequest body, HttpServletRequest request) {
    return ApiResponse.success(catalogService.updateSku(id, body), request.getRequestId());
  }

  @PutMapping("/admin/skus/{id}/status")
  public ApiResponse<SkuResponse> skuStatus(@PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest body, HttpServletRequest request) {
    return ApiResponse.success(catalogService.changeSkuStatus(id, body), request.getRequestId());
  }

  @GetMapping("/admin/skus/{id}/price-history")
  public ApiResponse<Page<SkuPriceHistoryResponse>> skuPriceHistory(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    int safeSize = Math.min(Math.max(size, 1), 100);
    return ApiResponse.success(
        catalogService.getSkuPriceHistory(id, PageRequest.of(Math.max(page, 0), safeSize)),
        request.getRequestId());
  }
}
