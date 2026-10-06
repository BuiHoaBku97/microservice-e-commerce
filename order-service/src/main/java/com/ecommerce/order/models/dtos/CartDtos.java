package com.ecommerce.order.models.dtos;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class CartDtos {
    private CartDtos() { }
    public record CartItemRequest(@NotNull Long skuId, @NotNull @Min(1) @Max(10) Integer quantity,
                                  @NotNull @Min(1) Long displayedUnitPrice) { }
    public record CartResponse(Long id, Long userId, Long version, List<Item> items) {
        public record Item(Long skuId, Integer quantity, Long displayedUnitPrice) { }
    }
    public record InventoryResponse(Long skuId, Integer onHand, Integer reserved, Integer available, Long version) { }
    public record InventoryMutationRequest(@NotNull Integer quantity, String reason, Long version) { }
}
