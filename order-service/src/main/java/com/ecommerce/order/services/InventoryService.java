package com.ecommerce.order.services;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Inventory;
import com.ecommerce.order.models.entities.InventoryIdempotencyRecord;
import com.ecommerce.order.models.entities.Reservation;
import com.ecommerce.order.models.entities.StockMovement;
import com.ecommerce.order.models.entities.StockMovementType;
import com.ecommerce.order.repositories.InventoryIdempotencyRepository;
import com.ecommerce.order.repositories.InventoryRepository;
import com.ecommerce.order.repositories.ReservationRepository;
import com.ecommerce.order.repositories.StockMovementRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryService {
  private final InventoryRepository inventoryRepository;
  private final RedissonClient redissonClient;
  private final TransactionTemplate transactionTemplate;
  private final ReservationRepository reservationRepository;
  private final StockMovementRepository movementRepository;
  private final InventoryIdempotencyRepository idempotencyRepository;
  private final long lockWaitMs;
  private final ThreadLocal<Long> actorContext = new ThreadLocal<>();

  public InventoryService(InventoryRepository inventoryRepository) {
    this.inventoryRepository = inventoryRepository;
    this.redissonClient = null;
    this.transactionTemplate = null;
    this.reservationRepository = null;
    this.movementRepository = null;
    this.idempotencyRepository = null;
    this.lockWaitMs = 0;
  }

  @Autowired
  public InventoryService(
      InventoryRepository inventoryRepository,
      ReservationRepository reservationRepository,
      StockMovementRepository movementRepository,
      InventoryIdempotencyRepository idempotencyRepository,
      RedissonClient redissonClient,
      PlatformTransactionManager transactionManager,
      @Value("${app.inventory.lock.wait-ms:2000}") long lockWaitMs) {
    this.inventoryRepository = inventoryRepository;
    this.reservationRepository = reservationRepository;
    this.movementRepository = movementRepository;
    this.idempotencyRepository = idempotencyRepository;
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

  public Page<Inventory> list(Long skuId, Pageable pageable) {
    return skuId == null
        ? inventoryRepository.findAll(pageable)
        : inventoryRepository.findBySkuId(skuId, pageable);
  }

  public Page<StockMovement> movements(Long skuId, Pageable pageable) {
    return movementRepository.findBySkuIdOrderByCreatedAtDesc(skuId, pageable);
  }

  public Inventory initialize(Long skuId, String idempotencyKey, Long actorUserId) {
    return idempotentMutation(
        skuId, "INITIALIZE", "", idempotencyKey, actorUserId, () -> initialize(skuId));
  }

  public Inventory receipt(
      Long skuId,
      CartDtos.InventoryMutationRequest request,
      String idempotencyKey,
      Long actorUserId) {
    return idempotentMutation(
        skuId,
        "RECEIPT",
        request.quantity() + "|" + request.reason(),
        idempotencyKey,
        actorUserId,
        () -> receipt(skuId, request));
  }

  public Inventory adjustment(
      Long skuId,
      CartDtos.InventoryMutationRequest request,
      String idempotencyKey,
      Long actorUserId) {
    return idempotentMutation(
        skuId,
        "ADJUSTMENT",
        request.quantity() + "|" + request.reason() + "|" + request.version(),
        idempotencyKey,
        actorUserId,
        () -> adjustment(skuId, request));
  }

  private Inventory idempotentMutation(
      Long skuId,
      String operation,
      String payload,
      String idempotencyKey,
      Long actorUserId,
      Supplier<Inventory> mutation) {
    String key = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (key.isEmpty() || key.length() > 100)
      throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
    String fingerprint = sha256(operation + "|" + skuId + "|" + payload);
    return executeCommand(
        Map.of(skuId, 1),
        () -> {
          InventoryIdempotencyRecord existing =
              idempotencyRepository.findByKeyForUpdate(key).orElse(null);
          if (existing != null) {
            if (!existing.getRequestFingerprint().equals(fingerprint)) {
              throw new com.ecommerce.order.exceptions.IdempotencyKeyConflictException();
            }
            String[] snapshot = existing.getResponseBody().split(":");
            Inventory replay = new Inventory();
            replay.setSkuId(skuId);
            replay.setOnHand(Integer.parseInt(snapshot[0]));
            replay.setReserved(Integer.parseInt(snapshot[1]));
            replay.setVersion(Long.parseLong(snapshot[2]));
            return replay;
          }
          InventoryIdempotencyRecord record =
              idempotencyRepository.save(
                  new InventoryIdempotencyRecord(key, fingerprint, operation, skuId));
          actorContext.set(actorUserId);
          Inventory updatedInventory;
          try {
            updatedInventory = mutation.get();
          } finally {
            actorContext.remove();
          }
          record.complete(
              updatedInventory.getOnHand()
                  + ":"
                  + updatedInventory.getReserved()
                  + ":"
                  + updatedInventory.getVersion());
          return updatedInventory;
        });
  }

  private String sha256(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
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
              Inventory saved = inventoryRepository.save(inventory);
              movement(
                  saved,
                  StockMovementType.INITIALIZE,
                  0,
                  0,
                  null,
                  null,
                  actorContext.get(),
                  UUID.randomUUID(),
                  "INITIALIZE");
              return saved;
            });
  }

  @Transactional
  public Inventory receipt(Long skuId, CartDtos.InventoryMutationRequest request) {
    if (request.quantity() == null || request.quantity() <= 0)
      throw new IllegalArgumentException("RECEIPT_QUANTITY_INVALID");
    Inventory inventory = initialize(skuId);
    inventory.setOnHand(inventory.getOnHand() + request.quantity());
    inventory.setVersion(inventory.getVersion() + 1);
    inventory.setUpdatedAt(Instant.now());
    Inventory saved = inventoryRepository.save(inventory);
    movement(
        saved,
        StockMovementType.RECEIPT,
        request.quantity(),
        0,
        null,
        null,
        actorContext.get(),
        UUID.randomUUID(),
        request.reason());
    return saved;
  }

  @Transactional
  public Inventory adjustment(Long skuId, CartDtos.InventoryMutationRequest request) {
    Inventory inventory =
        inventoryRepository
            .findById(skuId)
            .orElseThrow(() -> new IllegalArgumentException("SKU chưa được khởi tạo tồn kho"));
    if (request.quantity() == null || request.quantity() == 0)
      throw new IllegalArgumentException("INVENTORY_ADJUSTMENT_INVALID");
    if (request.version() != null && !request.version().equals(inventory.getVersion()))
      throw new IllegalStateException("VERSION_CONFLICT");
    int next = inventory.getOnHand() + request.quantity();
    if (next < inventory.getReserved())
      throw new IllegalStateException("INVENTORY_BELOW_RESERVED");
    inventory.setOnHand(next);
    inventory.setVersion(inventory.getVersion() + 1);
    inventory.setUpdatedAt(Instant.now());
    Inventory saved = inventoryRepository.save(inventory);
    movement(
        saved,
        StockMovementType.ADJUSTMENT,
        request.quantity(),
        0,
        null,
        null,
        actorContext.get(),
        UUID.randomUUID(),
        request.reason());
    return saved;
  }

  public void reserve(
      Long orderId, Map<Long, Integer> quantities, Instant expiresAt, UUID operationId) {
    withInventoryLocks(
        quantities,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> reserveInTransaction(orderId, quantities, expiresAt, operationId)));
  }

  public <T> T executeCommand(Map<Long, Integer> quantities, Supplier<T> action) {
    final Object[] result = new Object[1];
    withInventoryLocks(
        quantities, () -> result[0] = transactionTemplate.execute(status -> action.get()));
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  public void reserveInCurrentTransaction(
      Long orderId, Map<Long, Integer> quantities, Instant expiresAt, UUID operationId) {
    reserveInTransaction(orderId, quantities, expiresAt, operationId);
  }

  public void commitInCurrentTransaction(Long orderId, String eventId, UUID operationId) {
    commitInTransaction(orderId, eventId, operationId);
  }

  public void releaseInCurrentTransaction(Long orderId, String reason, UUID operationId) {
    releaseInTransaction(orderId, reason, operationId);
  }

  public void commit(Long orderId, String paymentEventId, UUID operationId) {
    Reservation reservation = requireReservation(orderId);
    Map<Long, Integer> quantities =
        reservation.getItems().stream()
            .collect(Collectors.toMap(item -> item.getSkuId(), item -> item.getQuantity()));
    withInventoryLocks(
        quantities,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> commitInTransaction(orderId, paymentEventId, operationId)));
  }

  public void release(Long orderId, String reason, UUID operationId) {
    Reservation reservation = requireReservation(orderId);
    Map<Long, Integer> quantities =
        reservation.getItems().stream()
            .collect(Collectors.toMap(item -> item.getSkuId(), item -> item.getQuantity()));
    withInventoryLocks(
        quantities,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> releaseInTransaction(orderId, reason, operationId)));
  }

  public void reserve(Map<Long, Integer> quantities) {
    withInventoryLocks(
        quantities,
        () -> transactionTemplate.executeWithoutResult(status -> reserveInTransaction(quantities)));
  }

  private void reserveInTransaction(
      Long orderId, Map<Long, Integer> quantities, Instant expiresAt, UUID operationId) {
    Reservation existing = reservationRepository.findByOrderIdForUpdate(orderId).orElse(null);
    if (existing != null) {
      if (existing.getStatus() == com.ecommerce.order.models.entities.ReservationStatus.ACTIVE)
        return;
      throw new IllegalStateException("INVALID_RESERVATION_STATE");
    }
    reserveInventory(quantities, orderId, null, operationId);
    Reservation reservation = new Reservation(orderId, expiresAt);
    quantities.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(entry -> reservation.addItem(entry.getKey(), entry.getValue()));
    Reservation savedReservation = reservationRepository.saveAndFlush(reservation);
    Long reservationId = savedReservation.getId();
    for (var entry : quantities.entrySet()) {
      Inventory inventory = inventoryRepository.findById(entry.getKey()).orElseThrow();
      movement(
          inventory,
          StockMovementType.RESERVE,
          0,
          entry.getValue(),
          orderId,
          reservationId,
          null,
          operationId,
          "CHECKOUT");
    }
  }

  private void reserveInTransaction(Map<Long, Integer> quantities) {
    reserveInventory(quantities, null, null, UUID.randomUUID());
  }

  private void reserveInventory(
      Map<Long, Integer> quantities, Long orderId, Long reservationId, UUID operationId) {
    List<Inventory> inventoryList =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> inventoryBySkuId =
        inventoryList.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    if (!inventoryBySkuId.keySet().containsAll(quantities.keySet())) {
      throw new IllegalArgumentException("INVENTORY_NOT_INITIALIZED");
    }
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = inventoryBySkuId.get(skuId);
          int available = inventory.getOnHand() - inventory.getReserved();
          if (available < quantity) {
            throw new IllegalStateException("INSUFFICIENT_STOCK");
          }
          inventory.setReserved(inventory.getReserved() + quantity);
          inventory.setVersion(inventory.getVersion() + 1);
          inventory.setUpdatedAt(Instant.now());
          if (orderId == null) {
            movement(
                inventory,
                StockMovementType.RESERVE,
                0,
                quantity,
                null,
                reservationId,
                null,
                operationId,
                "RESERVE");
          }
        });
  }

  public void release(Map<Long, Integer> quantities) {
    withInventoryLocks(
        quantities,
        () -> transactionTemplate.executeWithoutResult(status -> releaseInTransaction(quantities)));
  }

  public void finalizeReservation(Map<Long, Integer> quantities) {
    withInventoryLocks(
        quantities,
        () ->
            transactionTemplate.executeWithoutResult(
                status -> finalizeReservationInTransaction(quantities)));
  }

  private void finalizeReservationInTransaction(Map<Long, Integer> quantities) {
    List<Inventory> inventoryList =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> inventoryBySkuId =
        inventoryList.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = inventoryBySkuId.get(skuId);
          if (inventory == null) {
            throw new IllegalStateException("INVALID_INVENTORY_RESERVATION");
          }
          inventory.finalizeReservation(quantity);
        });
  }

  private void commitInTransaction(Long orderId, String eventId, UUID operationId) {
    Reservation reservation =
        reservationRepository
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalStateException("RESERVATION_NOT_FOUND"));
    if (!reservation.commit()) {
      return;
    }
    Map<Long, Integer> quantities =
        reservation.getItems().stream()
            .collect(Collectors.toMap(item -> item.getSkuId(), item -> item.getQuantity()));
    List<Inventory> inventoryRows =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> inventoryBySku =
        inventoryRows.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = inventoryBySku.get(skuId);
          if (inventory == null) {
            throw new IllegalStateException("INVALID_INVENTORY_RESERVATION");
          }
          inventory.finalizeReservation(quantity);
          movement(
              inventory,
              StockMovementType.COMMIT,
              -quantity,
              -quantity,
              orderId,
              reservation.getId(),
              null,
              eventId,
              operationId,
              "PAYMENT_SUCCEEDED");
        });
  }

  private void releaseInTransaction(Long orderId, String reason, UUID operationId) {
    Reservation reservation =
        reservationRepository
            .findByOrderIdForUpdate(orderId)
            .orElseThrow(() -> new IllegalStateException("RESERVATION_NOT_FOUND"));
    if (!reservation.release(reason)) {
      return;
    }
    Map<Long, Integer> quantities =
        reservation.getItems().stream()
            .collect(Collectors.toMap(item -> item.getSkuId(), item -> item.getQuantity()));
    List<Inventory> inventoryRows =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> inventoryBySku =
        inventoryRows.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = inventoryBySku.get(skuId);
          if (inventory == null || inventory.getReserved() < quantity)
            throw new IllegalStateException("INVALID_INVENTORY_RESERVATION");
          inventory.setReserved(inventory.getReserved() - quantity);
          inventory.setVersion(inventory.getVersion() + 1);
          inventory.setUpdatedAt(Instant.now());
          movement(
              inventory,
              StockMovementType.RELEASE,
              0,
              -quantity,
              orderId,
              reservation.getId(),
              null,
              operationId,
              reason);
        });
  }

  private void releaseInTransaction(Map<Long, Integer> quantities) {
    List<Inventory> inventoryList =
        inventoryRepository.findBySkuIdInForUpdate(quantities.keySet().stream().sorted().toList());
    Map<Long, Inventory> inventoryBySkuId =
        inventoryList.stream().collect(Collectors.toMap(Inventory::getSkuId, Function.identity()));
    quantities.forEach(
        (skuId, quantity) -> {
          Inventory inventory = inventoryBySkuId.get(skuId);
          if (inventory == null || inventory.getReserved() < quantity) {
            throw new IllegalStateException("INVALID_INVENTORY_RESERVATION");
          }
          inventory.setReserved(inventory.getReserved() - quantity);
          inventory.setVersion(inventory.getVersion() + 1);
          inventory.setUpdatedAt(Instant.now());
        });
  }

  private CartDtos.InventoryAvailabilityResponse response(Long skuId, Inventory inventory) {
    int available = inventory == null ? 0 : inventory.getOnHand() - inventory.getReserved();
    return new CartDtos.InventoryAvailabilityResponse(skuId, available);
  }

  private Reservation requireReservation(Long orderId) {
    if (reservationRepository == null)
      throw new IllegalStateException("RESERVATION_NOT_CONFIGURED");
    return reservationRepository
        .findByOrderId(orderId)
        .orElseThrow(() -> new IllegalStateException("RESERVATION_NOT_FOUND"));
  }

  private void movement(
      Inventory inventory,
      StockMovementType type,
      int onHandDelta,
      int reservedDelta,
      Long orderId,
      Long reservationId,
      Long actorUserId,
      UUID operationId,
      String reason) {
    movement(
        inventory,
        type,
        onHandDelta,
        reservedDelta,
        orderId,
        reservationId,
        actorUserId,
        null,
        operationId,
        reason);
  }

  private void movement(
      Inventory inventory,
      StockMovementType type,
      int onHandDelta,
      int reservedDelta,
      Long orderId,
      Long reservationId,
      Long actorUserId,
      String sourceEventId,
      UUID operationId,
      String reason) {
    if (movementRepository != null)
      movementRepository.save(
          new StockMovement(
              inventory,
              type,
              onHandDelta,
              reservedDelta,
              orderId,
              reservationId,
              actorUserId,
              sourceEventId,
              operationId,
              reason));
  }

  private void withInventoryLocks(Map<Long, Integer> quantities, Runnable action) {
    if (quantities == null || quantities.isEmpty()) {
      return;
    }
    if (redissonClient == null || transactionTemplate == null) {
      throw new IllegalStateException("INVENTORY_LOCK_NOT_CONFIGURED");
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
                    throw new IllegalStateException(
                        "Không thể lấy khóa tồn kho trong thời gian cho phép");
                  }
                  acquiredLocks.add(lock);
                } catch (InterruptedException exception) {
                  Thread.currentThread().interrupt();
                  throw new IllegalStateException(
                      "Đã bị gián đoạn khi chờ khóa tồn kho", exception);
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
