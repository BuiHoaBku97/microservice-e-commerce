package com.ecommerce.order.models.dtos;

import com.ecommerce.order.models.entities.OrderStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class CartDtos {
  private CartDtos() {}

  public record CartItemRequest(
      @NotNull Long skuId,
      @NotNull @Min(1) @Max(10) Integer quantity,
      @Min(1) Long displayedUnitPrice) {}

  public record CartResponse(Long id, Long userId, Long version, List<Item> items) {
    public record Item(Long skuId, Integer quantity, Long displayedUnitPrice) {}
  }

  public record InventoryResponse(
      Long skuId, Integer onHand, Integer reserved, Integer available, Long version) {}

  public record InventoryAvailabilityResponse(Long skuId, Integer available) {}

  public record InventoryMutationRequest(
      @NotNull Integer quantity, @NotBlank @Size(max = 200) String reason, Long version) {}

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
      String orderNumber,
      Long userId,
      String salesChannel,
      OrderStatus status,
      Long subtotal,
      Long shippingFee,
      Long totalAmount,
      String currency,
      String paymentMethod,
      String customerNote,
      Long version,
      Instant createdAt,
      Instant paymentDueAt,
      ShippingAddressResponse shippingAddress,
      List<OrderItemResponse> items) {}

  public record ShippingAddressResponse(
      Long addressId,
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province) {}

  public record OrderItemResponse(
      Long skuId,
      Long productId,
      String skuCode,
      String productName,
      String color,
      String size,
      Integer quantity,
      Long unitPrice,
      Long lineTotal,
      String currency) {}

  public record OrderStatusHistoryResponse(
      Long id, OrderStatus status, String source, String reason, Instant createdAt) {}

  public record VersionRequest(@NotNull Long version) {}

  public record CheckoutRequest(
      @NotNull Long cartVersion,
      @NotNull Long addressId,
      @NotBlank @Pattern(regexp = "MOCK_ONLINE") String paymentMethod,
      @Size(max = 1000) String customerNote,
      @NotNull @jakarta.validation.constraints.NotEmpty List<@Valid CheckoutItem> expectedItems) {
    public CheckoutRequest(
        Long cartVersion, Long addressId, List<CheckoutItem> expectedItems) {
      this(cartVersion, addressId, "MOCK_ONLINE", null, expectedItems);
    }
  }

  public record CheckoutItem(
      @NotNull Long skuId,
      @NotNull @Min(1) @Max(10) Integer quantity,
      @NotNull @Min(1) Long expectedUnitPrice) {}
}
