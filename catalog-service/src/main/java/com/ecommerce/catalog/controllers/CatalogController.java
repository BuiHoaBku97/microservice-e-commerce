package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.dtos.requests.ChangeStatusRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.ApiResponse;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SearchProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuPriceHistoryResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;
import com.ecommerce.catalog.models.dtos.responses.PageResponse;
import com.ecommerce.catalog.services.CatalogSearchService;
import com.ecommerce.catalog.services.CatalogService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CatalogController {
  private final CatalogService catalogService;
  private final CatalogSearchService searchService;

  public CatalogController(CatalogService catalogService, CatalogSearchService searchService) {
    this.catalogService = catalogService;
    this.searchService = searchService;
  }

  @GetMapping("/categories")
  public ApiResponse<PageResponse<CategoryResponse>> categories(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    int safeSize = Math.min(Math.max(size, 1), 100);
    return ApiResponse.success(PageResponse.from(catalogService.listActiveCategories(
        PageRequest.of(Math.max(page, 0), safeSize,
            org.springframework.data.domain.Sort.by("name").ascending().and(
                org.springframework.data.domain.Sort.by("id").ascending())))), request.getRequestId());
  }

  @GetMapping("/products/search")
  public ApiResponse<PageResponse<SearchProductResponse>> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) Long categoryId,
      @RequestParam(required = false) Long minPrice,
      @RequestParam(required = false) Long maxPrice,
      @RequestParam(defaultValue = "relevance") String sort,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
      throw new IllegalArgumentException("INVALID_PRICE_RANGE");
    }
    return ApiResponse.success(
        PageResponse.from(searchService.search(q, categoryId, minPrice, maxPrice, sort, page, size)),
        request.getRequestId());
  }

  @GetMapping("/products/{id}")
  public ApiResponse<ProductResponse> product(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(catalogService.getPublicProduct(id), request.getRequestId());
  }

  @PostMapping("/admin/categories")
  public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
      @Valid @RequestBody CreateCategoryRequest createCategoryRequest, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                catalogService.createCategory(createCategoryRequest), request.getRequestId()));
  }

  @PostMapping("/admin/products")
  public ResponseEntity<ApiResponse<ProductResponse>> createProduct(
      @Valid @RequestBody CreateProductRequest createProductRequest, HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                catalogService.createProduct(createProductRequest), request.getRequestId()));
  }

  @PostMapping("/admin/products/{productId}/skus")
  public ResponseEntity<ApiResponse<SkuResponse>> createSku(
      @PathVariable Long productId,
      @Valid @RequestBody CreateSkuRequest createSkuRequest,
      HttpServletRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(
            ApiResponse.success(
                catalogService.createSku(productId, createSkuRequest), request.getRequestId()));
  }

  @GetMapping("/admin/categories")
  public ApiResponse<List<CategoryResponse>> adminCategories(HttpServletRequest request) {
    return ApiResponse.success(catalogService.listAllCategories(), request.getRequestId());
  }

  @PatchMapping("/admin/categories/{id}")
  public ApiResponse<CategoryResponse> updateCategory(
      @PathVariable Long id,
      @Valid @RequestBody UpdateCategoryRequest updateCategoryRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        catalogService.updateCategory(id, updateCategoryRequest), request.getRequestId());
  }

  @GetMapping("/admin/products")
  public ApiResponse<List<ProductResponse>> adminProducts(HttpServletRequest request) {
    return ApiResponse.success(catalogService.listAllProducts(), request.getRequestId());
  }

  @GetMapping("/admin/products/{id}")
  public ApiResponse<ProductResponse> adminProduct(
      @PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(catalogService.getAdminProduct(id), request.getRequestId());
  }

  @PatchMapping("/admin/products/{id}")
  public ApiResponse<ProductResponse> updateProduct(
      @PathVariable Long id,
      @Valid @RequestBody UpdateProductRequest updateProductRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        catalogService.updateProduct(id, updateProductRequest), request.getRequestId());
  }

  @PutMapping("/admin/products/{id}/status")
  public ApiResponse<ProductResponse> productStatus(
      @PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest changeStatusRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        catalogService.changeProductStatus(id, changeStatusRequest), request.getRequestId());
  }

  @PatchMapping("/admin/skus/{id}")
  public ApiResponse<SkuResponse> updateSku(
      @PathVariable Long id,
      @Valid @RequestBody UpdateSkuRequest updateSkuRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        catalogService.updateSku(id, updateSkuRequest), request.getRequestId());
  }

  @PutMapping("/admin/skus/{id}/status")
  public ApiResponse<SkuResponse> skuStatus(
      @PathVariable Long id,
      @Valid @RequestBody ChangeStatusRequest changeStatusRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        catalogService.changeSkuStatus(id, changeStatusRequest), request.getRequestId());
  }

  @GetMapping("/admin/skus/{id}/price-history")
  public ApiResponse<PageResponse<SkuPriceHistoryResponse>> skuPriceHistory(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    int safeSize = Math.min(Math.max(size, 1), 100);
    return ApiResponse.success(
        PageResponse.from(catalogService.getSkuPriceHistory(id, PageRequest.of(Math.max(page, 0), safeSize))),
        request.getRequestId());
  }
}
