package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;

import java.util.List;

public interface CatalogService {
    CategoryResponse createCategory(CreateCategoryRequest request);
    List<CategoryResponse> listActiveCategories();
    ProductResponse createProduct(CreateProductRequest request);
    SkuResponse createSku(Long productId, CreateSkuRequest request);
    ProductResponse getPublicProduct(Long productId);
    List<ProductResponse> searchPublicProducts(String keyword);
}
