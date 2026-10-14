package com.ecommerce.order.models.dtos;

import com.ecommerce.order.models.entities.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class CartDtos {
  private CartDtos() {}

  public record CartItemRequest(
      @NotNull Long skuId,
      @NotNull @Min(1) @Max(10) Integer quantity,
      @NotNull @Min(1) Long displayedUnitPrice) {}

  public record CartResponse(Long id, Long userId, Long version, List<Item> items) {
    public record Item(Long skuId, Integer quantity, Long displayedUnitPrice) {}
  }

  public record InventoryResponse(
      Long skuId, Integer onHand, Integer reserved, Integer available, Long version) {}

  public record InventoryAvailabilityResponse(Long skuId, Integer available) {}

  public record InventoryMutationRequest(@NotNull Integer quantity, String reason, Long version) {}

  public record StockMovementResponse(
      Long id,
      Long skuId,
      String movementType,
      Integer onHandDelta,
      Integer reservedDelta,
      Integer onHandAfter,
      Integer reservedAfter,
      Long orderId,
      Long reservationId,
      Long actorUserId,
      String sourceEventId,
      java.util.UUID operationId,
      String reason,
      Instant createdAt) {}

  public record OrderResponse(
      Long id,
      Long userId,
      OrderStatus status,
      Long totalAmount,
      String currency,
      Long version,
      Instant createdAt,
      Instant paymentDueAt,
      ShippingAddressResponse shippingAddress,
      List<OrderItemResponse> items) {}

  public record ShippingAddressResponse(
      Long addressId,
      String recipientName,
      String recipientPhone,
      String ward,
      String addressLine,
      String district,
      String province) {}

  public record OrderItemResponse(Long skuId, Integer quantity, Long unitPrice, Long lineTotal) {}

  public record OrderStatusHistoryResponse(
      Long id, OrderStatus status, String source, String reason, Instant createdAt) {}

  public record CheckoutRequest(
      @NotNull Long cartVersion,
      @NotNull Long addressId,
      @NotNull @jakarta.validation.constraints.NotEmpty List<@Valid CheckoutItem> expectedItems) {}

  public record CheckoutItem(
      @NotNull Long skuId,
      @NotNull @Min(1) @Max(10) Integer quantity,
      @NotNull @Min(1) Long expectedUnitPrice) {}
}
