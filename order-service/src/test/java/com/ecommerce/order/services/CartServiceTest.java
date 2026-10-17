package com.ecommerce.order.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogReadClient;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.repositories.CartRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CartServiceTest {
  @Test
  void addShouldRejectSkuThatIsNotSellable() {
    CartRepository carts = mock(CartRepository.class);
    CatalogReadClient catalog = mock(CatalogReadClient.class);
    when(catalog.getSku(10L))
        .thenReturn(
            new CatalogCheckoutItem(
                10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 150_000L, "VND", 1L, false));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                new CartService(carts, catalog)
                    .add(7L, new CartDtos.CartItemRequest(10L, 1, 150_000L), 0L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("SKU_NOT_SELLABLE");

    verify(carts, never()).save(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void addShouldStoreOneLineAfterCatalogConfirmsSku() {
    CartRepository carts = mock(CartRepository.class);
    CatalogReadClient catalog = mock(CatalogReadClient.class);
    Cart cart = new Cart();
    cart.setUserId(7L);
    cart.touch();
    org.springframework.test.util.ReflectionTestUtils.setField(cart, "version", 0L);
    when(carts.findByUserId(7L)).thenReturn(Optional.of(cart));
    when(carts.save(cart)).thenReturn(cart);
    when(catalog.getSku(10L))
        .thenReturn(
            new CatalogCheckoutItem(
                10L, 20L, "SKU-10", "Sản phẩm", "RED", "M", 150_000L, "VND", 1L, true));

    Cart result =
        new CartService(carts, catalog)
            .add(7L, new CartDtos.CartItemRequest(10L, 1, 150_000L), 0L);

    org.assertj.core.api.Assertions.assertThat(result.getItems()).hasSize(1);
    org.assertj.core.api.Assertions.assertThat(result.getItems().getFirst().getSkuId()).isEqualTo(10L);
    verify(catalog).getSku(10L);
    verify(carts).save(cart);
  }
}
