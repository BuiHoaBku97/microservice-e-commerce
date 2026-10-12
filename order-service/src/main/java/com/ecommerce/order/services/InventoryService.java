package com.ecommerce.order.services;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Inventory;
import com.ecommerce.order.repositories.InventoryRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
  private final InventoryRepository inventoryRepository;
  private final RedissonClient redissonClient;
  private final TransactionTemplate transactionTemplate;
  private final long lockWaitMs;

  public InventoryService(InventoryRepository inventoryRepository) {
    this.inventoryRepository = inventoryRepository;
    this.redissonClient = null;
    this.transactionTemplate = null;
    this.lockWaitMs = 0;
  }

  @Autowired
  public InventoryService(
      InventoryRepository inventoryRepository,
      RedissonClient redissonClient,
      PlatformTransactionManager transactionManager,
      @Value("${app.inventory.lock.wait-ms:2000}") long lockWaitMs) {
    this.inventoryRepository = inventoryRepository;
    this.redissonClient = redissonClient;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.lockWaitMs = lockWaitMs;
  }

  public List<CartDtos.InventoryAvailabilityResponse> availability(List<Long> skuIds) {
    Map<Long, Inventory> byId =
        inventoryRepository.findBySkuIdIn(skuIds).stream()
            .collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    return skuIds.stream().distinct().map(id -> response(id, byId.get(id))).toList();
  }

  @Transactional
  public Inventory initialize(Long skuId) {
    return inventoryRepository
        .findById(skuId)
        .orElseGet(
            () -> {
              Inventory inventory = new Inventory();
              inventory.setSkuId(skuId);
              inventory.setCreatedAt(Instant.now());
              inventory.setUpdatedAt(Instant.now());
              return inventoryRepository.save(inventory);
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
    return inventoryRepository.save(inventory);
  }

  @Transactional
  public Inventory adjustment(Long skuId, CartDtos.InventoryMutationRequest request) {
    Inventory inventory =
            inventoryRepository
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
    return inventoryRepository.save(inventory);
  }

  public void reserve(Map<Long, Integer> quantities) {
    withInventoryLocks(quantities, () -> transactionTemplate.executeWithoutResult(
                                          status -> reserveInTransaction(quantities)));
  }

  private void reserveInTransaction(Map<Long, Integer> quantities) {
    List<Inventory> inventoryList =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> bySkuId =
        inventoryList.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    if (!bySkuId.keySet().containsAll(quantities.keySet())) {
      throw new IllegalArgumentException("SKU chưa được khởi tạo tồn kho");
    }
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = bySkuId.get(skuId);
          int available = inventory.getOnHand() - inventory.getReserved();
          if (available < quantity) {
            throw new IllegalStateException("Sản phẩm không đủ tồn kho");
          }
          inventory.setReserved(inventory.getReserved() + quantity);
          inventory.setVersion(inventory.getVersion() + 1);
          inventory.setUpdatedAt(Instant.now());
        });
  }

  public void release(Map<Long, Integer> quantities) {
    withInventoryLocks(quantities, () -> transactionTemplate.executeWithoutResult(
        status -> releaseInTransaction(quantities)));
  }

  private void releaseInTransaction(Map<Long, Integer> quantities) {
    List<Inventory> inventoryList =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> bySkuId =
        inventoryList.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = bySkuId.get(skuId);
          if (inventory == null || inventory.getReserved() < quantity) {
            throw new IllegalStateException("INVALID_INVENTORY_RESERVATION");
          }
          inventory.setReserved(inventory.getReserved() - quantity);
          inventory.setVersion(inventory.getVersion() + 1);
          inventory.setUpdatedAt(Instant.now());
        });
  }

  private CartDtos.InventoryAvailabilityResponse response(Long id, Inventory inventory) {
    int available = inventory == null ? 0 : inventory.getOnHand() - inventory.getReserved();
    return new CartDtos.InventoryAvailabilityResponse(id, available);
  }

  private void withInventoryLocks(Map<Long, Integer> quantities, Runnable action) {
    if (quantities == null || quantities.isEmpty()) {
      return;
    }
    if (redissonClient == null || transactionTemplate == null) {
      throw new IllegalStateException("Khóa tồn kho chưa được cấu hình");
    }
    List<RLock> acquiredLocks = new ArrayList<>();
    try {
      quantities.keySet().stream()
          .sorted(Comparator.naturalOrder())
          .map(skuId -> redissonClient.getLock("inventory:sku:" + skuId))
          .forEach(
              lock -> {
                try {
                  if (!lock.tryLock(lockWaitMs, -1, TimeUnit.MILLISECONDS)) {
                    throw new IllegalStateException("Không thể lấy khóa tồn kho trong thời gian cho phép");
                  }
                  acquiredLocks.add(lock);
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException("Đã bị gián đoạn khi chờ khóa tồn kho", exception);
                }
              });
      action.run();
    } finally {
      for (int index = acquiredLocks.size() - 1; index >= 0; index--) {
        RLock lock = acquiredLocks.get(index);
        if (lock.isHeldByCurrentThread()) {
          lock.unlock();
        }
      }
    }
  }
}
