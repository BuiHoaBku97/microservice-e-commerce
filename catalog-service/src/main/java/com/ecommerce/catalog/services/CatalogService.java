package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.requests.ChangeStatusRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.UpdateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.CheckoutItemResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuPriceHistoryResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CatalogService {
  CategoryResponse createCategory(CreateCategoryRequest request);

  List<CategoryResponse> listActiveCategories();

  ProductResponse createProduct(CreateProductRequest request);

  SkuResponse createSku(Long productId, CreateSkuRequest request);

  ProductResponse getPublicProduct(Long productId);

  List<ProductResponse> searchPublicProducts(String keyword);

  List<CheckoutItemResponse> getCheckoutItems(List<Long> skuIds);

  CheckoutItemResponse getSkuForInventory(Long skuId);

  List<CategoryResponse> listAllCategories();
  CategoryResponse updateCategory(Long id, UpdateCategoryRequest request);
  List<ProductResponse> listAllProducts();
  ProductResponse getAdminProduct(Long id);
  ProductResponse updateProduct(Long id, UpdateProductRequest request);
  ProductResponse changeProductStatus(Long id, ChangeStatusRequest request);
  SkuResponse updateSku(Long id, UpdateSkuRequest request);
  SkuResponse changeSkuStatus(Long id, ChangeStatusRequest request);
  Page<SkuPriceHistoryResponse> getSkuPriceHistory(Long id, Pageable pageable);
}
