package com.ecommerce.catalog.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.models.entities.Category;
import com.ecommerce.catalog.models.entities.Product;
import com.ecommerce.catalog.models.entities.Sku;
import com.ecommerce.catalog.models.entities.Status;
import com.ecommerce.catalog.repositories.CategoryRepository;
import com.ecommerce.catalog.repositories.ProductRepository;
import com.ecommerce.catalog.repositories.SkuRepository;
import com.ecommerce.catalog.services.impls.CatalogServiceImpl;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CatalogServiceImplTest {
  @Mock private CategoryRepository categoryRepository;
  @Mock private ProductRepository productRepository;
  @Mock private SkuRepository skuRepository;
  @InjectMocks private CatalogServiceImpl catalogService;

  @Test
  void createCategory_shouldNormalizeCodeAndSlug() {
    when(categoryRepository.existsByCodeIgnoreCase("PHONE")).thenReturn(false);
    when(categoryRepository.existsBySlugIgnoreCase("dien-thoai")).thenReturn(false);
    when(categoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    CategoryResponse result =
        catalogService.createCategory(
            new CreateCategoryRequest(" phone ", "Điện thoại", " Dien-Thoai "));

    assertThat(result.code()).isEqualTo("PHONE");
    assertThat(result.slug()).isEqualTo("dien-thoai");
  }

  @Test
  void getCheckoutItems_shouldReturnAuthoritativeActivePrice() {
    Category category = new Category("PHONE", "Điện thoại", "dien-thoai");
    Product product = new Product(category, "Sản phẩm", "", List.of());
    product.changeStatus(Status.ACTIVE);
    Sku sku = new Sku(product, "SKU-1", "RED", "M", 180_000L);
    ReflectionTestUtils.setField(sku, "id", 1L);
    when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));

    var result = catalogService.getCheckoutItems(List.of(1L));

    assertThat(result)
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.skuId()).isEqualTo(1L);
              assertThat(item.unitPrice()).isEqualTo(180_000L);
              assertThat(item.sellable()).isTrue();
            });
  }

  @Test
  void getCheckoutItems_shouldRejectInactiveProduct() {
    Category category = new Category("PHONE", "Điện thoại", "dien-thoai");
    Product product = new Product(category, "Sản phẩm", "", List.of());
    Sku sku = new Sku(product, "SKU-1", "RED", "M", 180_000L);
    ReflectionTestUtils.setField(sku, "id", 1L);
    when(skuRepository.findById(1L)).thenReturn(Optional.of(sku));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> catalogService.getCheckoutItems(List.of(1L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SKU_NOT_SELLABLE");
  }
}
