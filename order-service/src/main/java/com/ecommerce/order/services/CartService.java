package com.ecommerce.order.services;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.repositories.CartRepository;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class CartService {
  private final CartRepository cartRepository;

  public CartService(CartRepository cartRepository) {
    this.cartRepository = cartRepository;
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
      item.setDisplayedUnitPrice(request.displayedUnitPrice());
      item.touch();
      cart.getItems().add(item);
    } else {
      int quantity = item.getQuantity() + request.quantity();
      if (quantity > 10)
        throw new IllegalArgumentException("Số lượng mỗi SKU trong giỏ không được vượt quá 10");
      item.setQuantity(quantity);
      item.setDisplayedUnitPrice(request.displayedUnitPrice());
      item.touch();
    }
    cart.touch();
    return cartRepository.save(cart);
  }

  @Transactional
  public Cart update(
      Long userId, Long skuId, CartDtos.CartItemRequest request, Long expectedVersion) {
    Cart cart = checkedCart(userId, expectedVersion);
    CartItem item =
        cart.getItems().stream()
            .filter(i -> Objects.equals(i.getSkuId(), skuId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("SKU không tồn tại trong giỏ hàng"));
    item.setQuantity(request.quantity());
    item.setDisplayedUnitPrice(request.displayedUnitPrice());
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
}
