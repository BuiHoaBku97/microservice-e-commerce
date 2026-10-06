package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.dtos.requests.CreateCategoryRequest;
import com.ecommerce.catalog.models.dtos.responses.CategoryResponse;
import com.ecommerce.catalog.repositories.CategoryRepository;
import com.ecommerce.catalog.repositories.ProductRepository;
import com.ecommerce.catalog.repositories.SkuRepository;
import com.ecommerce.catalog.services.impls.CatalogServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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

        CategoryResponse result = catalogService.createCategory(
                new CreateCategoryRequest(" phone ", "Điện thoại", " Dien-Thoai "));

        assertThat(result.code()).isEqualTo("PHONE");
        assertThat(result.slug()).isEqualTo("dien-thoai");
    }
}
