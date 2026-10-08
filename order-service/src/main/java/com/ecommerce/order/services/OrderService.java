package com.ecommerce.order.services;

import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.repositories.OrderRepository;
import jakarta.transaction.Transactional;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  private final OrderRepository orders;
  private final CartService carts;
  private final InventoryService inventory;

  public OrderService(OrderRepository orders, CartService carts, InventoryService inventory) {
    this.orders = orders;
    this.carts = carts;
    this.inventory = inventory;
  }

  @Transactional
  public Order createFromCart(Long userId) {
    Cart cart = carts.getOrCreate(userId);
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("CART_EMPTY");
    }

    Map<Long, Integer> quantities =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getSkuId(),
                    item -> item.getQuantity(),
                    Integer::sum,
                    LinkedHashMap::new));
    inventory.reserve(quantities);

    long totalAmount =
        cart.getItems().stream()
            .mapToLong(item -> item.getDisplayedUnitPrice() * item.getQuantity())
            .sum();
    Order order = new Order(userId, totalAmount, "VND");
    cart.getItems()
        .forEach(
            item ->
                order.addItem(
                    new OrderItem(
                        item.getSkuId(), item.getQuantity(), item.getDisplayedUnitPrice())));
    carts.clear(userId, cart.getVersion());
    return orders.save(order);
  }

  @Transactional
  public List<Order> listByUser(Long userId) {
    return orders.findByUserIdOrderByCreatedAtDesc(userId);
  }

  @Transactional
  public Order getByUser(Long userId, Long orderId) {
    return orders
        .findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public Order cancel(Long userId, Long orderId) {
    Order order = getByUser(userId, orderId);
    Map<Long, Integer> quantities =
        order.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getSkuId(),
                    item -> item.getQuantity(),
                    Integer::sum,
                    LinkedHashMap::new));
    inventory.release(quantities);
    order.cancel();
    return orders.save(order);
  }
}
