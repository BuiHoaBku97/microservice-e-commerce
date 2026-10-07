package com.ecommerce.order.services;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Inventory;
import com.ecommerce.order.repositories.InventoryRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
  private final InventoryRepository inventories;

  public InventoryService(InventoryRepository inventories) {
    this.inventories = inventories;
  }

  public List<CartDtos.InventoryResponse> availability(List<Long> skuIds) {
    Map<Long, Inventory> byId =
        inventories.findBySkuIdIn(skuIds).stream()
            .collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    return skuIds.stream().distinct().map(id -> response(id, byId.get(id))).toList();
  }

  @Transactional
  public Inventory initialize(Long skuId) {
    return inventories
        .findById(skuId)
        .orElseGet(
            () -> {
              Inventory inventory = new Inventory();
              inventory.setSkuId(skuId);
              inventory.setCreatedAt(Instant.now());
              inventory.setUpdatedAt(Instant.now());
              return inventories.save(inventory);
            });
  }

  @Transactional
  public Inventory receipt(Long skuId, CartDtos.InventoryMutationRequest request) {
    if (request.quantity() == null || request.quantity() <= 0)
      throw new IllegalArgumentException("Số lượng nhập kho phải lớn hơn 0");
    Inventory inventory = initialize(skuId);
    inventory.setOnHand(inventory.getOnHand() + request.quantity());
    inventory.setVersion(inventory.getVersion() + 1);
    inventory.setUpdatedAt(Instant.now());
    return inventories.save(inventory);
  }

  @Transactional
  public Inventory adjustment(Long skuId, CartDtos.InventoryMutationRequest request) {
    Inventory inventory =
        inventories
            .findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("SKU chưa được khởi tạo tồn kho"));
    if (request.quantity() == null || request.quantity() == 0)
      throw new IllegalArgumentException("Mức điều chỉnh tồn kho không được bằng 0");
    if (request.version() != null && !request.version().equals(inventory.getVersion()))
      throw new IllegalStateException("VERSION_CONFLICT");
    int next = inventory.getOnHand() + request.quantity();
    if (next < inventory.getReserved())
      throw new IllegalStateException("Điều chỉnh làm tồn kho thấp hơn số lượng đã giữ");
    inventory.setOnHand(next);
    inventory.setVersion(inventory.getVersion() + 1);
    inventory.setUpdatedAt(Instant.now());
    return inventories.save(inventory);
  }

  private CartDtos.InventoryResponse response(Long id, Inventory inventory) {
    if (inventory == null) return new CartDtos.InventoryResponse(id, 0, 0, 0, 0L);
    return new CartDtos.InventoryResponse(
        id,
        inventory.getOnHand(),
        inventory.getReserved(),
        inventory.getOnHand() - inventory.getReserved(),
        inventory.getVersion());
  }
}
