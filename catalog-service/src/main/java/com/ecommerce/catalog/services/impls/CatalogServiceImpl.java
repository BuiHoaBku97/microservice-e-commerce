package com.ecommerce.catalog.services.impls;

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
import com.ecommerce.catalog.models.entities.Category;
import com.ecommerce.catalog.models.entities.Product;
import com.ecommerce.catalog.models.entities.Sku;
import com.ecommerce.catalog.models.entities.Status;
import com.ecommerce.catalog.repositories.CategoryRepository;
import com.ecommerce.catalog.repositories.ProductRepository;
import com.ecommerce.catalog.repositories.SkuRepository;
import com.ecommerce.catalog.repositories.SkuPriceHistoryRepository;
import com.ecommerce.catalog.models.entities.SkuPriceHistory;
import org.springframework.beans.factory.annotation.Value;
import com.ecommerce.catalog.services.CatalogService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;

@Service
public class CatalogServiceImpl implements CatalogService {
  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final SkuRepository skuRepository;
  private final SkuPriceHistoryRepository priceHistoryRepository;
  private final String defaultImageUrl;

  public CatalogServiceImpl(
      CategoryRepository categoryRepository,
      ProductRepository productRepository,
      SkuRepository skuRepository,
      SkuPriceHistoryRepository priceHistoryRepository,
      @Value("${app.catalog.default-image-url:https://res.cloudinary.com/demo/image/upload/product-placeholder.jpg}") String defaultImageUrl) {
    this.categoryRepository = categoryRepository;
    this.productRepository = productRepository;
    this.skuRepository = skuRepository;
    this.priceHistoryRepository = priceHistoryRepository;
    this.defaultImageUrl = defaultImageUrl;
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
    List<String> images = request.imageUrls() == null ? List.of(defaultImageUrl) : request.imageUrls();
    Product product = new Product(category, request.name().trim(), request.description(), images);
    return toResponse(productRepository.save(product), List.of());
  }

  @Override
  @Transactional
  @CacheEvict(cacheNames = "product", key = "#result.productId()")
  public SkuResponse createSku(Long productId, CreateSkuRequest request) {
    Product product =
        productRepository
            .findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    if (skuRepository.existsByCodeIgnoreCase(request.code().trim())) {
      throw new IllegalStateException("SKU_EXISTS");
    }
    String color = normalizeVariant(request.color());
    String size = normalizeVariant(request.size());
    if (skuRepository.existsByProductIdAndColorAndSize(productId, color, size)) {
      throw new IllegalStateException("VARIANT_EXISTS");
    }
    Sku sku =
        new Sku(
            product,
            request.code().trim(),
            color,
            size,
            request.price());
    Sku saved = skuRepository.save(sku);
    priceHistoryRepository.save(new SkuPriceHistory(saved, null, saved.getPrice(), "INITIAL_PRICE"));
    return toResponse(saved);
  }

  @Override
  @Transactional(readOnly = true)
  @Cacheable(cacheNames = "product", key = "#productId")
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

  @Override @Transactional(readOnly = true)
  public List<CategoryResponse> listAllCategories() {
    return categoryRepository.findAll().stream().map(this::toResponse).toList();
  }

  @Override @Transactional
  public CategoryResponse updateCategory(Long id, UpdateCategoryRequest request) {
    Category category = categoryRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("CATEGORY_NOT_FOUND"));
    requireVersion(category.getVersion(), request.version());
    category.update(request.name().trim(), request.slug().trim().toLowerCase());
    category.changeStatus(request.status());
    return toResponse(categoryRepository.save(category));
  }

  @Override @Transactional(readOnly = true)
  public List<ProductResponse> listAllProducts() {
    return productRepository.findAll().stream()
        .map(product -> toResponse(product, skuRepository.findByProductIdOrderByIdAsc(product.getId())))
        .toList();
  }

  @Override @Transactional(readOnly = true)
  public ProductResponse getAdminProduct(Long id) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    return toResponse(product, skuRepository.findByProductIdOrderByIdAsc(id));
  }

  @Override @Transactional
  @CacheEvict(cacheNames = "product", key = "#id")
  public ProductResponse updateProduct(Long id, UpdateProductRequest request) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    requireVersion(product.getVersion(), request.version());
    Category category = categoryRepository.findById(request.categoryId())
        .orElseThrow(() -> new IllegalArgumentException("CATEGORY_NOT_FOUND"));
    List<String> images = request.imageUrls() == null ? product.getImageUrls() : request.imageUrls();
    product.update(category, request.name().trim(), request.description(), images);
    return toResponse(productRepository.save(product), skuRepository.findByProductIdOrderByIdAsc(id));
  }

  @Override @Transactional
  @CacheEvict(cacheNames = "product", key = "#id")
  public ProductResponse changeProductStatus(Long id, ChangeStatusRequest request) {
    Product product = productRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("PRODUCT_NOT_FOUND"));
    requireVersion(product.getVersion(), request.version());
    if (request.status() == Status.ACTIVE
        && skuRepository.findByProductIdAndStatusOrderByIdAsc(id, Status.ACTIVE).isEmpty()) {
      throw new IllegalStateException("PRODUCT_REQUIRES_ACTIVE_SKU");
    }
    product.changeStatus(request.status());
    return toResponse(productRepository.save(product), skuRepository.findByProductIdOrderByIdAsc(id));
  }

  @Override @Transactional
  @CacheEvict(cacheNames = "product", key = "#result.productId()")
  public SkuResponse updateSku(Long id, UpdateSkuRequest request) {
    Sku sku = skuRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("SKU_NOT_FOUND"));
    requireVersion(sku.getVersion(), request.version());
    String color = request.color() == null ? sku.getColor() : normalizeVariant(request.color());
    String size = request.size() == null ? sku.getSize() : normalizeVariant(request.size());
    if ((!color.equals(sku.getColor()) || !size.equals(sku.getSize()))
        && skuRepository.existsByProductIdAndColorAndSize(sku.getProduct().getId(), color, size)) {
      throw new IllegalStateException("VARIANT_EXISTS");
    }
    Long oldPrice = sku.getPrice();
    Long newPrice = request.price() == null ? oldPrice : request.price();
    sku.update(newPrice, color, size);
    Sku saved = skuRepository.save(sku);
    if (!oldPrice.equals(newPrice)) {
      priceHistoryRepository.save(new SkuPriceHistory(saved, oldPrice, newPrice, request.reason()));
    }
    return toResponse(saved);
  }

  @Override @Transactional
  @CacheEvict(cacheNames = "product", key = "#result.productId()")
  public SkuResponse changeSkuStatus(Long id, ChangeStatusRequest request) {
    Sku sku = skuRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("SKU_NOT_FOUND"));
    requireVersion(sku.getVersion(), request.version());
    sku.changeStatus(request.status());
    return toResponse(skuRepository.save(sku));
  }

  @Override
  @Transactional(readOnly = true)
  public Page<SkuPriceHistoryResponse> getSkuPriceHistory(Long id, Pageable pageable) {
    if (!skuRepository.existsById(id)) throw new IllegalArgumentException("SKU_NOT_FOUND");
    return priceHistoryRepository.findBySkuIdOrderByCreatedAtDesc(id, pageable)
        .map(SkuPriceHistoryResponse::from);
  }

  private void requireVersion(Long current, Long expected) {
    if (!java.util.Objects.equals(current, expected)) throw new IllegalStateException("VERSION_CONFLICT");
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
