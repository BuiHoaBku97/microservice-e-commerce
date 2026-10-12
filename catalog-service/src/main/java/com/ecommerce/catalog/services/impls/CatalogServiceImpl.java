package com.ecommerce.catalog.services.impls;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateProductRequest;
import com.ecommerce.catalog.models.dtos.requests.CreateSkuRequest;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.dtos.responses.CheckoutItemResponse;
import com.ecommerce.catalog.models.dtos.responses.ProductResponse;
import com.ecommerce.catalog.models.dtos.responses.SkuResponse;
import com.ecommerce.catalog.models.entities.Category;
import com.ecommerce.catalog.models.entities.Product;
import com.ecommerce.catalog.models.entities.Sku;
import com.ecommerce.catalog.models.entities.Status;
import com.ecommerce.catalog.repositories.CategoryRepository;
import com.ecommerce.catalog.repositories.ProductRepository;
import com.ecommerce.catalog.repositories.SkuRepository;
import com.ecommerce.catalog.services.CatalogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogServiceImpl implements CatalogService {
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;

  public CatalogServiceImpl(
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
  }

  @Override
  @Transactional
  public CategoryResponse createCategory(CreateCategoryRequest request) {
    String code = request.code().trim().toUpperCase();
    String slug = request.slug().trim().toLowerCase();
    if (categoryRepository.existsByCodeIgnoreCase(code)
        || categoryRepository.existsBySlugIgnoreCase(slug)) {
      throw new IllegalStateException("CATEGORY_EXISTS");
    }
    return toResponse(categoryRepository.save(new Category(code, request.name().trim(), slug)));
  }

  @Override
  @Transactional(readOnly = true)
  public List<CategoryResponse> listActiveCategories() {
    return categoryRepository.findByStatusOrderByNameAsc(Status.ACTIVE).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  @Transactional
  public ProductResponse createProduct(CreateProductRequest request) {
    Category category =
        categoryRepository
            .findById(request.categoryId())
            .orElseThrow(() -> new IllegalArgumentException("CATEGORY_NOT_FOUND"));
    List<String> images = request.imageUrls() == null ? List.of() : request.imageUrls();
    Product product = new Product(category, request.name().trim(), request.description(), images);
    return toResponse(productRepository.save(product), List.of());
  }

  @Override
  @Transactional
  public SkuResponse createSku(Long productId, CreateSkuRequest request) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    if (skuRepository.existsByCodeIgnoreCase(request.code().trim())) {
      throw new IllegalStateException("SKU_EXISTS");
    }
    Sku sku =
        new Sku(
            product,
            request.code().trim(),
            normalizeVariant(request.color()),
            normalizeVariant(request.size()),
            request.price());
    return toResponse(skuRepository.save(sku));
  }

  @Override
  @Transactional(readOnly = true)
  public ProductResponse getPublicProduct(Long productId) {
    Product product =
        productRepository
            .findById(productId)
            .filter(value -> value.getStatus() == Status.ACTIVE)
            .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    List<Sku> skus = skuRepository.findByProductIdAndStatusOrderByIdAsc(productId, Status.ACTIVE);
    if (skus.isEmpty()) {
      throw new IllegalArgumentException("PRODUCT_NOT_FOUND");
    }
    return toResponse(product, skus);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ProductResponse> searchPublicProducts(String keyword) {
    String normalized = keyword == null ? "" : keyword.trim();
    return (normalized.isBlank()
            ? productRepository.findByStatusOrderByNameAsc(Status.ACTIVE)
            : productRepository.findByStatusAndNameContainingIgnoreCaseOrderByNameAsc(
                Status.ACTIVE, normalized))
        .stream()
            .map(
                product ->
                    new ProductResponse(
                        product.getId(),
                        product.getName(),
                        product.getDescription(),
                        product.getCategory().getId(),
                        product.getImageUrls(),
                        product.getStatus(),
                        product.getVersion(),
                        skuRepository
                            .findByProductIdAndStatusOrderByIdAsc(product.getId(), Status.ACTIVE)
                            .stream()
                            .map(this::toResponse)
                            .toList()))
            .filter(product -> !product.skus().isEmpty())
            .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CheckoutItemResponse> getCheckoutItems(List<Long> skuIds) {
    if (skuIds == null
        || skuIds.isEmpty()
        || skuIds.size() > 100
        || skuIds.stream().anyMatch(id -> id == null || id <= 0)
        || skuIds.size() != skuIds.stream().distinct().count()) {
      throw new IllegalArgumentException("Danh sách SKU checkout không hợp lệ");
    }

    return skuIds.stream()
        .map(
            skuId -> {
              Sku sku =
                  skuRepository
                      .findById(skuId)
                      .orElseThrow(() -> new IllegalArgumentException("SKU_NOT_FOUND"));
              Product product = sku.getProduct();
              boolean sellable =
                  product.getStatus() == Status.ACTIVE && sku.getStatus() == Status.ACTIVE;
              if (!sellable) {
                throw new IllegalStateException("SKU_NOT_SELLABLE");
              }
              return new CheckoutItemResponse(
                  sku.getId(),
                  product.getId(),
                  sku.getCode(),
                  product.getName(),
                  sku.getColor(),
                  sku.getSize(),
                  sku.getPrice(),
                  sku.getCurrency(),
                  product.getVersion(),
                  true);
            })
        .toList();
  }

  private String normalizeVariant(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private CategoryResponse toResponse(Category category) {
    return new CategoryResponse(
        category.getId(),
        category.getCode(),
        category.getName(),
        category.getSlug(),
        category.getStatus(),
        category.getVersion());
  }

  private SkuResponse toResponse(Sku sku) {
    return new SkuResponse(
        sku.getId(),
        sku.getProduct().getId(),
        sku.getCode(),
        sku.getColor(),
        sku.getSize(),
        sku.getPrice(),
        sku.getCurrency(),
        sku.getStatus(),
        sku.getVersion());
  }

  private ProductResponse toResponse(Product product, List<Sku> skus) {
    return new ProductResponse(
        product.getId(),
        product.getName(),
        product.getDescription(),
        product.getCategory().getId(),
        product.getImageUrls(),
        product.getStatus(),
        product.getVersion(),
        skus.stream().map(this::toResponse).toList());
  }
}
