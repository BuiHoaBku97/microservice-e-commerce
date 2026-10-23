package com.ecommerce.order.services;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogReadClient;
import com.ecommerce.order.exceptions.CatalogUnavailableException;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.repositories.CartRepository;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final CartRepository cartRepository;
  private final CatalogReadClient catalogReadClient;

  public CartService(CartRepository cartRepository, CatalogReadClient catalogReadClient) {
    this.cartRepository = cartRepository;
    this.catalogReadClient = catalogReadClient;
  }

  @Transactional
  public Cart getOrCreate(Long userId) {
    return cartRepository
        .findByUserId(userId)
        .orElseGet(
            () -> {
              Cart cart = new Cart();
              cart.setUserId(userId);
              cart.touch();
              return cartRepository.save(cart);
            });
  }

  @Transactional
  public Cart getForCheckout(Long userId) {
    return cartRepository
        .findByUserIdForUpdate(userId)
        .orElseGet(() -> getOrCreate(userId));
  }

  @Transactional
  public Cart add(Long userId, CartDtos.CartItemRequest request, Long expectedVersion) {
    CatalogCheckoutItem catalogSku = requireSellableSku(request.skuId());
    Cart cart = checkedCart(userId, expectedVersion);
    CartItem item =
        cart.getItems().stream()
            .filter(i -> Objects.equals(i.getSkuId(), request.skuId()))
            .findFirst()
            .orElse(null);
    if (item == null) {
      item = new CartItem();
      item.setCart(cart);
      item.setSkuId(request.skuId());
      item.setQuantity(request.quantity());
      item.setDisplayedUnitPrice(catalogSku.unitPrice());
      item.touch();
      cart.getItems().add(item);
    } else {
      int quantity = item.getQuantity() + request.quantity();
      if (quantity > 10)
        throw new IllegalArgumentException("CART_ITEM_QUANTITY_EXCEEDED");
      item.setQuantity(quantity);
      item.setDisplayedUnitPrice(catalogSku.unitPrice());
      item.touch();
    }
    cart.touch();
    return cartRepository.save(cart);
  }

  @Transactional
  public Cart update(
      Long userId, Long skuId, CartDtos.CartItemRequest request, Long expectedVersion) {
    if (!Objects.equals(skuId, request.skuId())) {
      throw new IllegalArgumentException("SKU_ID_MISMATCH");
    }
    CatalogCheckoutItem catalogSku = requireSellableSku(skuId);
    Cart cart = checkedCart(userId, expectedVersion);
    CartItem item =
        cart.getItems().stream()
            .filter(i -> Objects.equals(i.getSkuId(), skuId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("SKU không tồn tại trong giỏ hàng"));
    item.setQuantity(request.quantity());
    item.setDisplayedUnitPrice(catalogSku.unitPrice());
    item.touch();
    cart.touch();
    return cartRepository.save(cart);
  }

  @Transactional
  public Cart remove(Long userId, Long skuId, Long expectedVersion) {
    Cart cart = checkedCart(userId, expectedVersion);
    cart.getItems().removeIf(i -> Objects.equals(i.getSkuId(), skuId));
    cart.touch();
    return cartRepository.save(cart);
  }

  @Transactional
  public void clear(Long userId, Long expectedVersion) {
    Cart cart = checkedCart(userId, expectedVersion);
    cart.getItems().clear();
    cart.touch();
  }

  private Cart checkedCart(Long userId, Long expectedVersion) {
    Cart cart = getOrCreate(userId);
    if (expectedVersion != null && !Objects.equals(expectedVersion, cart.getVersion()))
      throw new IllegalStateException("VERSION_CONFLICT");
    return cart;
  }

  private CatalogCheckoutItem requireSellableSku(Long skuId) {
    CatalogCheckoutItem sku;
    try {
      sku = catalogReadClient.getSku(skuId);
    } catch (feign.FeignException.NotFound exception) {
      throw new IllegalArgumentException("SKU_NOT_FOUND");
    } catch (feign.FeignException exception) {
      throw new CatalogUnavailableException();
    }
    if (sku == null || !sku.sellable()) {
      throw new IllegalStateException("SKU_NOT_SELLABLE");
    }
    return sku;
  }
}
