package com.ecommerce.order.services;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogCheckoutRequest;
import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.clients.IdentityCheckoutContext;
import com.ecommerce.order.clients.IdentityCheckoutContextRequest;
import com.ecommerce.order.clients.IdentityClient;
import com.ecommerce.order.clients.IdentityStatusClient;
import com.ecommerce.order.configs.CorrelationContext;
import com.ecommerce.order.exceptions.CatalogUnavailableException;
import com.ecommerce.order.exceptions.IdempotencyKeyConflictException;
import com.ecommerce.order.exceptions.IdentityUnavailableException;
import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.exceptions.PriceChangedException;
import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.models.entities.OrderStatusHistory;
import com.ecommerce.order.models.entities.PaymentEventHistory;
import com.ecommerce.order.models.entities.PaymentEventResult;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.repositories.OrderStatusHistoryRepository;
import com.ecommerce.order.repositories.PaymentEventHistoryRepository;
import jakarta.transaction.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class OrderService {
  public record CheckoutResult(Order order, boolean replayed) {}

  private final OrderRepository orderRepository;
  private final CartService cartService;
  private final InventoryService inventoryService;
  private final OutboxEventService outboxEventService;
  private final CatalogClient catalogClient;
  private final IdentityClient identityClient;
  private final IdentityStatusClient identityStatusClient;
  private final DatabaseClock databaseClock;
  private final long paymentTimeoutSeconds;
  private final long shippingFee;
  private final PaymentEventHistoryRepository paymentEventHistoryRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;

  @Autowired
  public OrderService(
      OrderRepository orderRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      IdentityClient identityClient,
      IdentityStatusClient identityStatusClient,
      DatabaseClock databaseClock,
      PaymentEventHistoryRepository paymentEventHistoryRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      @Value("${app.order.payment-timeout-seconds:900}") long paymentTimeoutSeconds,
      @Value("${app.order.shipping-fee:30000}") long shippingFee) {
    this.orderRepository = orderRepository;
    this.cartService = cartService;
    this.inventoryService = inventoryService;
    this.outboxEventService = outboxEventService;
    this.catalogClient = catalogClient;
    this.identityClient = identityClient;
    this.identityStatusClient = identityStatusClient;
    this.databaseClock = databaseClock;
    this.paymentEventHistoryRepository = paymentEventHistoryRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    if (paymentTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("PAYMENT_DUE_INVALID");
    }
    if (shippingFee < 0) {
      throw new IllegalArgumentException("SHIPPING_FEE_INVALID");
    }
    this.paymentTimeoutSeconds = paymentTimeoutSeconds;
    this.shippingFee = shippingFee;
  }

  OrderService(
      OrderRepository orderRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      IdentityClient identityClient,
      IdentityStatusClient identityStatusClient,
      DatabaseClock databaseClock,
      PaymentEventHistoryRepository paymentEventHistoryRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      long paymentTimeoutSeconds) {
    this(
        orderRepository,
        cartService,
        inventoryService,
        outboxEventService,
        catalogClient,
        identityClient,
        identityStatusClient,
        databaseClock,
        paymentEventHistoryRepository,
        orderStatusHistoryRepository,
        paymentTimeoutSeconds,
        30_000L);
  }

  public Order createFromCart(Long userId) {
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(
        cart, userId, null, null, "MOCK_ONLINE", null, null, null, null);
  }

  public Order createFromCart(
      Long userId, CartDtos.CheckoutRequest checkoutRequest, String authorization) {
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(
        cart,
        userId,
        checkoutRequest == null ? null : checkoutRequest.cartVersion(),
        checkoutRequest == null ? null : checkoutRequest.expectedItems(),
        checkoutRequest == null ? "MOCK_ONLINE" : checkoutRequest.paymentMethod(),
        checkoutRequest == null ? null : checkoutRequest.customerNote(),
        null,
        null,
        null);
  }

  public CheckoutResult checkout(
      Long userId,
      CartDtos.CheckoutRequest checkoutRequest,
      String authorization,
      String idempotencyKey) {
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    String requestHash = checkoutHash(checkoutRequest);
    Order existing =
        orderRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
      requireActiveAccount(userId);
      return replayOrReject(existing, requestHash);
    }

    IdentityCheckoutContext checkoutContext;
    try {
      checkoutContext =
          identityClient.getCheckoutContext(
              new IdentityCheckoutContextRequest(checkoutRequest.addressId()), authorization);
    } catch (feign.FeignException exception) {
      if (exception.status() == 403) {
        throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
      }
      if (exception.status() == 404) {
        throw new IllegalArgumentException("ADDRESS_NOT_FOUND");
      }
      throw new IdentityUnavailableException();
    }

    Cart cart = cartService.getForCheckout(userId);
    existing = orderRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
      return replayOrReject(existing, requestHash);
    }

    return new CheckoutResult(
        createFromCart(
            cart,
            userId,
            checkoutRequest.cartVersion(),
            checkoutRequest.expectedItems(),
            checkoutRequest.paymentMethod(),
            checkoutRequest.customerNote(),
            normalizedKey,
            requestHash,
            checkoutContext),
        false);
  }

  private Order createFromCart(
      Cart cart,
      Long userId,
      Long expectedCartVersion,
      List<CartDtos.CheckoutItem> expectedItems,
      String paymentMethod,
      String customerNote,
      String idempotencyKey,
      String idempotencyHash,
      IdentityCheckoutContext checkoutContext) {
    if (cart.getItems().isEmpty()) {
      throw new IllegalArgumentException("CART_EMPTY");
    }

    if (expectedCartVersion != null && !Objects.equals(expectedCartVersion, cart.getVersion())) {
      throw new IllegalStateException("VERSION_CONFLICT");
    }
    Map<Long, Integer> quantities =
        cart.getItems().stream()
            .collect(
                Collectors.toMap(
                    CartItem::getSkuId, CartItem::getQuantity, Integer::sum, LinkedHashMap::new));
    List<Long> skuIds = cart.getItems().stream().map(CartItem::getSkuId).distinct().toList();
    Map<Long, CartDtos.CheckoutItem> expectedBySku = new LinkedHashMap<>();
    if (expectedItems != null) {
      for (CartDtos.CheckoutItem expectedItem : expectedItems) {
        expectedBySku.compute(
            expectedItem.skuId(),
            (skuId, previous) -> {
              if (previous == null) {
                return expectedItem;
              }
              if (!Objects.equals(previous.expectedUnitPrice(), expectedItem.expectedUnitPrice())) {
                throw new IllegalStateException("CART_CHANGED");
              }
              return new CartDtos.CheckoutItem(
                  skuId,
                  previous.quantity() + expectedItem.quantity(),
                  previous.expectedUnitPrice());
            });
      }

      boolean differentSkuSet = !expectedBySku.keySet().equals(quantities.keySet());
      boolean differentQuantity =
          expectedBySku.entrySet().stream()
              .anyMatch(
                  entry ->
                      !Objects.equals(entry.getValue().quantity(), quantities.get(entry.getKey())));
      if (differentSkuSet || differentQuantity) {
        throw new IllegalStateException("CART_CHANGED");
      }
    }
    List<CatalogCheckoutItem> catalogItems;
    try {
      catalogItems =
          catalogClient.getCheckoutItems(new CatalogCheckoutRequest(skuIds));
    } catch (feign.FeignException exception) {
      if (exception.status() == 404 || exception.status() == 409) {
        throw new IllegalStateException("SKU_NOT_SELLABLE");
      }
      throw new CatalogUnavailableException();
    }
    Map<Long, CatalogCheckoutItem> catalogBySku =
        catalogItems.stream().collect(Collectors.toMap(CatalogCheckoutItem::skuId, item -> item));
    if (catalogBySku.size() != skuIds.size()
        || catalogBySku.values().stream().anyMatch(item -> !item.sellable())) {
      throw new IllegalStateException("SKU_NOT_SELLABLE");
    }
    for (var cartItem : cart.getItems()) {
      CatalogCheckoutItem catalogItem = catalogBySku.get(cartItem.getSkuId());
      if (!Objects.equals(catalogItem.unitPrice(), cartItem.getDisplayedUnitPrice())
          || (expectedBySku.containsKey(catalogItem.skuId())
              && !Objects.equals(
                  expectedBySku.get(catalogItem.skuId()).expectedUnitPrice(),
                  catalogItem.unitPrice()))) {
        throw new PriceChangedException();
      }
    }
    long subtotal =
        cart.getItems().stream()
            .mapToLong(item -> catalogBySku.get(item.getSkuId()).unitPrice() * item.getQuantity())
            .sum();
    UUID operationId =
        operationId(idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey);
    return executeInventoryCommand(
        quantities,
        () -> {
          Instant paymentDueAt = databaseClock.deadlineAfter(paymentTimeoutSeconds);
          Order order =
              new Order(
                  userId,
                  subtotal,
                  shippingFee,
                  "VND",
                  paymentMethod,
                  normalizeCustomerNote(customerNote),
                  paymentDueAt);
          if (idempotencyKey != null) {
            order.setIdempotency(idempotencyKey, idempotencyHash);
          }
          if (checkoutContext != null) {
            IdentityCheckoutContext.AddressSnapshot address = checkoutContext.address();
            order.setShippingAddress(
                address.id(),
                address.recipientName(),
                address.recipientPhone(),
                address.addressLine(),
                address.ward(),
                address.district(),
                address.province());
          }
          cart.getItems()
              .forEach(
                  item ->
                      order.addItem(
                          new OrderItem(
                              item.getSkuId(),
                              catalogBySku.get(item.getSkuId()).productId(),
                              catalogBySku.get(item.getSkuId()).skuCode(),
                              catalogBySku.get(item.getSkuId()).productName(),
                              catalogBySku.get(item.getSkuId()).color(),
                              catalogBySku.get(item.getSkuId()).size(),
                              item.getQuantity(),
                              catalogBySku.get(item.getSkuId()).unitPrice(),
                              catalogBySku.get(item.getSkuId()).currency())));
          Order savedOrder = orderRepository.save(order);
          orderRepository.flush();
          inventoryService.reserveInCurrentTransaction(
              savedOrder.getId(), quantities, savedOrder.getPaymentDueAt(), operationId);
          cartService.clear(userId, cart.getVersion());
          recordStatus(savedOrder, "CHECKOUT", null);
          outboxEventService.enqueueOrderCreated(savedOrder);
          return savedOrder;
        });
  }

  private CheckoutResult replayOrReject(Order existing, String requestHash) {
    if (!Objects.equals(existing.getIdempotencyHash(), requestHash)) {
      throw new IdempotencyKeyConflictException();
    }
    return new CheckoutResult(existing, true);
  }

  private String normalizeIdempotencyKey(String idempotencyKey) {
    String normalizedKey = idempotencyKey == null ? "" : idempotencyKey.trim();
    if (normalizedKey.isEmpty()) {
      throw new IllegalArgumentException("IDEMPOTENCY_KEY_REQUIRED");
    }
    if (normalizedKey.length() > 100) {
      throw new IllegalArgumentException("IDEMPOTENCY_KEY_TOO_LONG");
    }
    return normalizedKey;
  }

  private String checkoutHash(CartDtos.CheckoutRequest request) {
    String canonical =
        request.cartVersion()
            + "|"
            + request.addressId()
            + "|"
            + request.paymentMethod()
            + "|"
            + normalizeCustomerNote(request.customerNote())
            + "|"
            + request.expectedItems().stream()
                .sorted(Comparator.comparing(CartDtos.CheckoutItem::skuId))
                .map(item -> item.skuId() + ":" + item.quantity() + ":" + item.expectedUnitPrice())
                .collect(Collectors.joining(";"));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("CHECKOUT_CONFIRMATION_CODE_FAILED", exception);
    }
  }

  private UUID operationId(String stableValue) {
    return UUID.nameUUIDFromBytes(stableValue.getBytes(StandardCharsets.UTF_8));
  }

  private <T> T executeInventoryCommand(
      Map<Long, Integer> quantities, java.util.function.Supplier<T> action) {
    return inventoryService.executeCommand(quantities, action);
  }

  @Transactional
  public Page<Order> listByUser(
      Long userId,
      com.ecommerce.order.models.entities.OrderStatus status,
      Instant from,
      Instant to,
      Pageable pageable) {
    validateTimeRange(from, to);
    Page<Order> orders = orderRepository.findForUser(userId, status, from, to, pageable);
    initializeItems(orders);
    return orders;
  }

  @Transactional
  public Order getByUser(Long userId, Long orderId) {
    return orderRepository
        .findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  public Order cancel(Long userId, Long orderId) {
    Order owned = getByUser(userId, orderId);
    Map<Long, Integer> quantities = quantities(owned);
    return executeInventoryCommand(
        quantities,
        () -> {
          Order order = getForUpdate(owned.getId());
          if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CANCELLED) {
            return order;
          }
          if (order.getStatus()
              != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
            throw new IllegalStateException("INVALID_STATE_TRANSITION");
          }
          Instant decisionTime = databaseClock.now();
          String cancellationReason =
              order.isPaymentExpiredAt(decisionTime) ? "PAYMENT_EXPIRED" : "CUSTOMER_CANCELLED";
          inventoryService.releaseInCurrentTransaction(
              order.getId(),
              cancellationReason,
              operationId("cancel:" + order.getId() + ":" + cancellationReason));
          if ("PAYMENT_EXPIRED".equals(cancellationReason)) {
            order.markPaymentExpired(decisionTime);
          } else {
            order.cancel(decisionTime);
          }
          Order savedOrder = orderRepository.save(order);
          recordStatus(savedOrder, "CUSTOMER_CANCEL", savedOrder.getCancellationReason());
          outboxEventService.enqueueOrderCancelled(savedOrder);
          return savedOrder;
        });
  }

  public Order handlePaymentSucceeded(PaymentEvent event) {
    Order orderSnapshot =
        orderRepository
            .findWithItemsById(event.orderId())
            .orElseGet(() -> getForUpdate(event.orderId()));
    return executeInventoryCommand(
        quantities(orderSnapshot),
        () -> {
          Order order = getForUpdate(event.orderId());
          if (isDuplicate(event)) {
            return order;
          }
          Instant decisionTime = databaseClock.now();
          String invalidReason = validatePayment(event, order, decisionTime);
          if (invalidReason != null) {
            rejectPayment(order, event, invalidReason, decisionTime);
            return orderRepository.save(order);
          }
          inventoryService.commitInCurrentTransaction(
              event.orderId(), event.eventId(), operationId(event.eventId()));
          order.confirm(event.paymentId(), decisionTime);
          paymentEventHistoryRepository.save(
              new PaymentEventHistory(
                  event.eventId(),
                  event.paymentId(),
                  event.orderId(),
                  PaymentEventResult.ACCEPTED,
                  "accepted"));
          Order savedOrder = orderRepository.save(order);
          recordStatus(savedOrder, "PAYMENT_SUCCEEDED", null);
          outboxEventService.enqueueOrderConfirmed(savedOrder);
          return savedOrder;
        });
  }

  public Order handlePaymentFailed(PaymentEvent event) {
    Order orderSnapshot =
        orderRepository
            .findWithItemsById(event.orderId())
            .orElseGet(() -> getForUpdate(event.orderId()));
    return executeInventoryCommand(
        quantities(orderSnapshot),
        () -> {
          Order order = getForUpdate(event.orderId());
          if (isDuplicate(event)) {
            return order;
          }
          Instant decisionTime = databaseClock.now();
          String invalidReason = validatePayment(event, order, decisionTime);
          if (invalidReason != null) {
            rejectPayment(order, event, invalidReason, decisionTime);
            return orderRepository.save(order);
          }
          if (order.getStatus()
              == com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
            inventoryService.releaseInCurrentTransaction(
                event.orderId(), "PAYMENT_FAILED", operationId(event.eventId()));
            order.markPaymentFailed(decisionTime);
          }
          paymentEventHistoryRepository.save(
              new PaymentEventHistory(
                  event.eventId(),
                  event.paymentId(),
                  event.orderId(),
                  PaymentEventResult.FAILED,
                  event.failureReason() == null ? "payment_failed" : event.failureReason()));
          Order savedOrder = orderRepository.save(order);
          if (savedOrder.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CANCELLED) {
            recordStatus(savedOrder, "PAYMENT_FAILED", savedOrder.getCancellationReason());
            outboxEventService.enqueueOrderCancelled(savedOrder);
          }
          return savedOrder;
        });
  }

  public int expirePendingPayments() {
    List<Order> expiredOrders = orderRepository.findExpiredPendingOrders();
    int expiredCount = 0;
    for (Order candidateOrder : expiredOrders) {
      CorrelationContext.set(UUID.randomUUID().toString());
      try {
      Order orderSnapshot =
          orderRepository.findWithItemsById(candidateOrder.getId()).orElse(candidateOrder);
      Boolean paymentExpired =
          executeInventoryCommand(
              quantities(orderSnapshot),
              () -> {
                Order order = getForUpdate(candidateOrder.getId());
                Instant decisionTime = databaseClock.now();
                if (order.getStatus()
                        != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT
                    || !order.isPaymentExpiredAt(decisionTime)) {
                  return false;
                }
                inventoryService.releaseInCurrentTransaction(
                    order.getId(), "PAYMENT_EXPIRED", operationId("expiry:" + order.getId()));
                order.markPaymentExpired(decisionTime);
                Order savedOrder = orderRepository.save(order);
                recordStatus(savedOrder, "PAYMENT_EXPIRY", savedOrder.getCancellationReason());
                outboxEventService.enqueueOrderCancelled(savedOrder);
                return true;
              });
      if (Boolean.TRUE.equals(paymentExpired)) {
        expiredCount++;
      }
      } finally {
        CorrelationContext.clear();
      }
    }
    return expiredCount;
  }

  private String validatePayment(PaymentEvent event, Order order, Instant decisionTime) {
    if (event.eventId() == null || event.eventId().isBlank()) {
      throw new IllegalArgumentException("EVENT_ID_REQUIRED");
    }
    if (event.paymentId() == null || event.paymentId() <= 0) {
      throw new IllegalArgumentException("INVALID_PAYMENT_ID");
    }
    if (order.getId() != null && !Objects.equals(event.orderId(), order.getId())) {
      throw new IllegalArgumentException("INVALID_ORDER_ID");
    }
    if (order.getStatus() != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      return "ORDER_NOT_PENDING";
    }
    if (order.isPaymentExpiredAt(decisionTime)) {
      return "PAYMENT_EXPIRED";
    }
    if (!Objects.equals(event.amount(), order.getTotalAmount())) {
      return "PAYMENT_AMOUNT_MISMATCH";
    }
    if (!Objects.equals(event.currency(), order.getCurrency())) {
      return "PAYMENT_CURRENCY_MISMATCH";
    }
    return null;
  }

  private String normalizeCustomerNote(String customerNote) {
    if (customerNote == null) {
      return null;
    }
    String normalized = customerNote.trim();
    return normalized.isEmpty() ? null : normalized;
  }

  private void requireActiveAccount(Long userId) {
    String status;
    try {
      status = identityStatusClient.getStatus(userId);
    } catch (feign.FeignException exception) {
      if (exception.status() == 403) {
        throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
      }
      throw new IdentityUnavailableException();
    } catch (Exception exception) {
      throw new IdentityUnavailableException();
    }
    if (!"ACTIVE".equals(status)) {
      throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
    }
  }

  private boolean isDuplicate(PaymentEvent event) {
    return event.eventId() != null
        && paymentEventHistoryRepository.existsByEventId(event.eventId());
  }

  private void rejectPayment(
      Order order, PaymentEvent event, String reason, Instant decisionTime) {
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      String cancellationReason =
          "PAYMENT_EXPIRED".equals(reason) ? "PAYMENT_EXPIRED" : "PAYMENT_MISMATCH";
      inventoryService.releaseInCurrentTransaction(
          event.orderId(), cancellationReason, operationId(event.eventId()));
      if ("PAYMENT_EXPIRED".equals(reason)) {
        order.markPaymentExpired(decisionTime);
        outboxEventService.enqueueOrderCancelled(order);
      } else {
        order.markPaymentMismatch(decisionTime);
      }
      recordStatus(order, "PAYMENT_REJECTED", cancellationReason);
    }
    paymentEventHistoryRepository.save(
        new PaymentEventHistory(
            event.eventId(),
            event.paymentId(),
            event.orderId(),
            PaymentEventResult.REVIEW,
            reason));
    outboxEventService.enqueueOrderConfirmationRejected(
        order, event.paymentId(), rejectionReason(order, reason));
  }

  private String rejectionReason(Order order, String reason) {
    if ("PAYMENT_EXPIRED".equals(reason)) {
      return "PAYMENT_EXPIRED";
    }
    if ("PAYMENT_AMOUNT_MISMATCH".equals(reason)
        || "PAYMENT_CURRENCY_MISMATCH".equals(reason)
        || "PAYMENT_DUE_AT_MISMATCH".equals(reason)) {
      return "PAYMENT_MISMATCH";
    }
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CANCELLED) {
      return "PAYMENT_EXPIRED".equals(order.getCancellationReason())
          ? "PAYMENT_EXPIRED"
          : "ORDER_CANCELLED";
    }
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CONFIRMED
        || order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.SHIPPING
        || order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.COMPLETED) {
      return "PAYMENT_ALREADY_ACCEPTED";
    }
    return switch (reason) {
      default -> "PAYMENT_MISMATCH";
    };
  }

  @Transactional
  public Order ship(Long orderId, Long expectedVersion) {
    Order order = getForUpdate(orderId);
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.SHIPPING) {
      return order;
    }
    requireVersion(order, expectedVersion);
    order.ship(databaseClock.now());
    Order savedOrder = orderRepository.save(order);
    recordStatus(savedOrder, "ADMIN_SHIP", null);
    outboxEventService.enqueueOrderShipping(savedOrder);
    savedOrder.getItems().size();
    return savedOrder;
  }

  @Transactional
  public Order complete(Long orderId, Long expectedVersion) {
    Order order = getForUpdate(orderId);
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.COMPLETED) {
      return order;
    }
    requireVersion(order, expectedVersion);
    order.complete(databaseClock.now());
    Order savedOrder = orderRepository.save(order);
    recordStatus(savedOrder, "ADMIN_COMPLETE", null);
    outboxEventService.enqueueOrderCompleted(savedOrder);
    savedOrder.getItems().size();
    return savedOrder;
  }

  private Order getForUpdate(Long orderId) {
    if (orderId == null) {
      throw new IllegalArgumentException("ORDER_ID_REQUIRED");
    }
    return orderRepository
        .findByIdForUpdate(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  private Map<Long, Integer> quantities(Order order) {
    return order.getItems().stream()
        .collect(
            Collectors.toMap(
                OrderItem::getSkuId, OrderItem::getQuantity, Integer::sum, LinkedHashMap::new));
  }

  @Transactional
  public List<OrderStatusHistory> historyByUser(Long userId, Long orderId) {
    getByUser(userId, orderId);
    return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
  }

  @Transactional
  public List<OrderStatusHistory> historyForAdmin(Long orderId) {
    if (!orderRepository.existsById(orderId)) {
      throw new OrderNotFoundException(orderId);
    }
    return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
  }

  @Transactional
  public Page<Order> listForAdmin(
      Long userId,
      com.ecommerce.order.models.entities.OrderStatus status,
      Instant from,
      Instant to,
      Pageable pageable) {
    validateTimeRange(from, to);
    Page<Order> orders = orderRepository.findForAdmin(userId, status, from, to, pageable);
    initializeItems(orders);
    return orders;
  }

  @Transactional
  public Order getForAdmin(Long orderId) {
    return orderRepository
        .findWithItemsById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public OrderReport report(Instant from, Instant to) {
    Map<String, Long> countByStatus = new LinkedHashMap<>();
    orderRepository
        .countByStatus(from, to)
        .forEach(
            row ->
                countByStatus.put(
                    ((com.ecommerce.order.models.entities.OrderStatus) row[0]).name(),
                    (Long) row[1]));
    return new OrderReport(countByStatus, orderRepository.completedOrderValue(from, to));
  }

  private void recordStatus(Order order, String source, String reason) {
    if (orderStatusHistoryRepository != null) {
      orderStatusHistoryRepository.save(new OrderStatusHistory(order, source, reason));
    }
  }

  private void requireVersion(Order order, Long expectedVersion) {
    if (expectedVersion == null) {
      throw new IllegalArgumentException("VERSION_REQUIRED");
    }
    if (!Objects.equals(order.getVersion(), expectedVersion)) {
      throw new IllegalStateException("VERSION_CONFLICT");
    }
  }

  private void validateTimeRange(Instant from, Instant to) {
    if (from != null && to != null && !from.isBefore(to)) {
      throw new IllegalArgumentException("INVALID_TIME_RANGE");
    }
  }

  private void initializeItems(Page<Order> orders) {
    List<Long> ids = orders.getContent().stream().map(Order::getId).toList();
    if (!ids.isEmpty()) {
      orderRepository.findAllWithItemsByIdIn(ids);
    }
  }

  public record OrderReport(Map<String, Long> countByStatus, long completedOrderValue) {}
}
