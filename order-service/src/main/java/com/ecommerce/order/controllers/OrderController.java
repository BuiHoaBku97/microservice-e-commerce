package com.ecommerce.order.controllers;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.services.CartService;
import com.ecommerce.order.services.InventoryService;
import com.ecommerce.order.services.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import com.ecommerce.order.models.entities.OrderStatus;
import com.ecommerce.order.models.entities.OrderStatusHistory;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
  private final CartService cartService;
  private final InventoryService inventoryService;
  private final OrderService orderService;

  public OrderController(
      CartService cartService, InventoryService inventoryService, OrderService orderService) {
    this.cartService = cartService;
    this.inventoryService = inventoryService;
    this.orderService = orderService;
  }

  @GetMapping("/cart")
  public CartDtos.CartResponse cart(Authentication authentication) {
    return response(cartService.getOrCreate(userId(authentication)));
  }

  @PostMapping("/cart/items")
  public CartDtos.CartResponse add(
      Authentication authentication,
      @RequestParam Long version,
      @Valid @RequestBody CartDtos.CartItemRequest request) {
    return response(cartService.add(userId(authentication), request, version));
  }

  @PatchMapping("/cart/items/{skuId}")
  public CartDtos.CartResponse update(
      Authentication authentication,
      @PathVariable Long skuId,
      @RequestParam Long version,
      @Valid @RequestBody CartDtos.CartItemRequest request) {
    return response(cartService.update(userId(authentication), skuId, request, version));
  }

  @DeleteMapping("/cart/items/{skuId}")
  public CartDtos.CartResponse remove(
      Authentication authentication, @PathVariable Long skuId, @RequestParam Long version) {
    return response(cartService.remove(userId(authentication), skuId, version));
  }

  @DeleteMapping("/cart")
  public ResponseEntity<Void> clear(Authentication authentication, @RequestParam Long version) {
    cartService.clear(userId(authentication), version);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/orders/checkout")
  public ResponseEntity<CartDtos.OrderResponse> createOrder(
      Authentication authentication,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody CartDtos.CheckoutRequest request) {
    OrderService.CheckoutResult result =
        orderService.checkout(
            userId(authentication),
            request,
            "Bearer " + authentication.getCredentials(),
            idempotencyKey);
    ResponseEntity.BodyBuilder response =
        result.replayed() ? ResponseEntity.ok() : ResponseEntity.status(HttpStatus.CREATED);
    if (result.replayed()) {
      response.header("Idempotency-Replayed", "true");
    }
    return response.body(orderResponse(result.order()));
  }

  @GetMapping("/orders")
  public List<CartDtos.OrderResponse> listOrders(Authentication authentication) {
    return orderService.listByUser(userId(authentication)).stream()
        .map(this::orderResponse)
        .toList();
  }

  @GetMapping("/orders/{orderId}")
  public CartDtos.OrderResponse order(Authentication authentication, @PathVariable Long orderId) {
    return orderResponse(orderService.getByUser(userId(authentication), orderId));
  }

  @GetMapping("/orders/{orderId}/history")
  public List<CartDtos.OrderStatusHistoryResponse> orderHistory(
      Authentication authentication, @PathVariable Long orderId) {
    return orderService.historyByUser(userId(authentication), orderId).stream()
        .map(this::historyResponse).toList();
  }

  @PostMapping("/orders/{orderId}/cancel")
  public CartDtos.OrderResponse cancelOrder(
      Authentication authentication, @PathVariable Long orderId) {
    return orderResponse(orderService.cancel(userId(authentication), orderId));
  }

  @PostMapping("/admin/orders/{orderId}/ship")
  public CartDtos.OrderResponse ship(@PathVariable Long orderId) {
    return orderResponse(orderService.ship(orderId));
  }

  @PostMapping("/admin/orders/{orderId}/complete")
  public CartDtos.OrderResponse complete(@PathVariable Long orderId) {
    return orderResponse(orderService.complete(orderId));
  }

  @GetMapping("/admin/orders")
  public Page<CartDtos.OrderResponse> adminOrders(
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) OrderStatus status,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return orderService.listForAdmin(userId, status, from, to,
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))).map(this::orderResponse);
  }

  @GetMapping("/admin/orders/{orderId}")
  public CartDtos.OrderResponse adminOrder(@PathVariable Long orderId) {
    return orderResponse(orderService.getForAdmin(orderId));
  }

  @GetMapping("/admin/orders/{orderId}/history")
  public List<CartDtos.OrderStatusHistoryResponse> adminOrderHistory(@PathVariable Long orderId) {
    return orderService.historyForAdmin(orderId).stream().map(this::historyResponse).toList();
  }

  @GetMapping("/admin/reports/orders")
  public OrderService.OrderReport orderReport(
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to) {
    return orderService.report(from, to);
  }

  @GetMapping("/inventory/availability")
  public List<CartDtos.InventoryAvailabilityResponse> availability(
      @RequestParam List<Long> skuIds) {
    if (skuIds.size() > 100) throw new IllegalArgumentException("Chỉ được kiểm tra tối đa 100 SKU");
    if (skuIds.isEmpty() || skuIds.stream().anyMatch(id -> id == null || id <= 0)) {
      throw new IllegalArgumentException("Danh sách SKU phải chứa các ID lớn hơn 0");
    }
    return inventoryService.availability(skuIds);
  }

  @PostMapping("/admin/inventory/{skuId}/initialize")
  public CartDtos.InventoryResponse initialize(@PathVariable Long skuId) {
    return toInventory(inventoryService.initialize(skuId));
  }

  @PostMapping("/admin/inventory/{skuId}/receipts")
  public CartDtos.InventoryResponse receipt(
      @PathVariable Long skuId, @Valid @RequestBody CartDtos.InventoryMutationRequest request) {
    return toInventory(inventoryService.receipt(skuId, request));
  }

  @PostMapping("/admin/inventory/{skuId}/adjustments")
  public CartDtos.InventoryResponse adjustment(
      @PathVariable Long skuId, @Valid @RequestBody CartDtos.InventoryMutationRequest request) {
    return toInventory(inventoryService.adjustment(skuId, request));
  }

  private Long userId(Authentication authentication) {
    return Long.valueOf(authentication.getName());
  }

  private CartDtos.CartResponse response(Cart cart) {
    return new CartDtos.CartResponse(
        cart.getId(),
        cart.getUserId(),
        cart.getVersion(),
        cart.getItems().stream()
            .map(
                i ->
                    new CartDtos.CartResponse.Item(
                        i.getSkuId(), i.getQuantity(), i.getDisplayedUnitPrice()))
            .toList());
  }

  private CartDtos.InventoryResponse toInventory(com.ecommerce.order.models.entities.Inventory i) {
    return new CartDtos.InventoryResponse(
        i.getSkuId(),
        i.getOnHand(),
        i.getReserved(),
        i.getOnHand() - i.getReserved(),
        i.getVersion());
  }

  private CartDtos.OrderResponse orderResponse(Order order) {
    return new CartDtos.OrderResponse(
        order.getId(),
        order.getUserId(),
        order.getStatus(),
        order.getTotalAmount(),
        order.getCurrency(),
        order.getVersion(),
        order.getCreatedAt(),
        order.getPaymentDueAt(),
        order.getShippingAddressId() == null
            ? null
            : new CartDtos.ShippingAddressResponse(
                order.getShippingAddressId(),
                order.getShippingRecipientName(),
                order.getShippingRecipientPhone(),
                order.getShippingAddressLine(),
                order.getShippingWard(),
                order.getShippingDistrict(),
                order.getShippingProvince()),
        order.getItems().stream().map(this::orderItemResponse).toList());
  }

  private CartDtos.OrderItemResponse orderItemResponse(OrderItem item) {
    return new CartDtos.OrderItemResponse(
        item.getSkuId(), item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
  }

  private CartDtos.OrderStatusHistoryResponse historyResponse(OrderStatusHistory history) {
    return new CartDtos.OrderStatusHistoryResponse(
        history.getId(), history.getStatus(), history.getSource(), history.getReason(),
        history.getCreatedAt());
  }
}
