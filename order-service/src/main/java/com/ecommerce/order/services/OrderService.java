package com.ecommerce.order.services;

import com.ecommerce.order.clients.CatalogCheckoutItem;
import com.ecommerce.order.clients.CatalogCheckoutRequest;
import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.clients.IdentityCheckoutContext;
import com.ecommerce.order.clients.IdentityCheckoutContextRequest;
import com.ecommerce.order.clients.IdentityClient;
import com.ecommerce.order.exceptions.CatalogUnavailableException;
import com.ecommerce.order.exceptions.IdentityUnavailableException;
import com.ecommerce.order.exceptions.IdempotencyKeyConflictException;
import com.ecommerce.order.exceptions.OrderNotFoundException;
import com.ecommerce.order.exceptions.PriceChangedException;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.models.entities.CartItem;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.models.entities.PaymentEventHistory;
import com.ecommerce.order.models.entities.PaymentEventResult;
import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.repositories.PaymentEventHistoryRepository;
import com.ecommerce.order.repositories.OrderStatusHistoryRepository;
import com.ecommerce.order.models.entities.OrderStatusHistory;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Service
public class OrderService {
  public record CheckoutResult(Order order, boolean replayed) {}

  private final OrderRepository ordersRepository;
  private final CartService cartService;
  private final InventoryService inventoryService;
  private final OutboxEventService outboxEventService;
  private final CatalogClient catalogClient;
  private final IdentityClient identityClient;
  private final long paymentTimeoutSeconds;
  private final PaymentEventHistoryRepository paymentEventHistoryRepository;
  private final OrderStatusHistoryRepository orderStatusHistoryRepository;

  @Autowired
  public OrderService(
      OrderRepository ordersRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      IdentityClient identityClient,
      PaymentEventHistoryRepository paymentEventHistoryRepository,
      OrderStatusHistoryRepository orderStatusHistoryRepository,
      @Value("${app.order.payment-timeout-seconds:900}") long paymentTimeoutSeconds) {
    this.ordersRepository = ordersRepository;
    this.cartService = cartService;
    this.inventoryService = inventoryService;
    this.outboxEventService = outboxEventService;
    this.catalogClient = catalogClient;
    this.identityClient = identityClient;
    this.paymentEventHistoryRepository = paymentEventHistoryRepository;
    this.orderStatusHistoryRepository = orderStatusHistoryRepository;
    if (paymentTimeoutSeconds <= 0) {
      throw new IllegalArgumentException("Thời hạn thanh toán phải lớn hơn 0");
    }
    this.paymentTimeoutSeconds = paymentTimeoutSeconds;
  }

  public OrderService(
      OrderRepository ordersRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      IdentityClient identityClient,
      long paymentTimeoutSeconds) {
    this(
        ordersRepository,
        cartService,
        inventoryService,
        outboxEventService,
        catalogClient,
        identityClient,
        null,
        null,
        paymentTimeoutSeconds);
  }

  public OrderService(
      OrderRepository ordersRepository,
      CartService cartService,
      InventoryService inventoryService,
      OutboxEventService outboxEventService,
      CatalogClient catalogClient,
      IdentityClient identityClient,
      PaymentEventHistoryRepository paymentEventHistoryRepository,
      long paymentTimeoutSeconds) {
    this(ordersRepository, cartService, inventoryService, outboxEventService, catalogClient,
        identityClient, paymentEventHistoryRepository, null, paymentTimeoutSeconds);
  }

  @Transactional
  public Order createFromCart(Long userId) {
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(cart, userId, null, null, null, null, null, null);
  }

  @Transactional
  public Order createFromCart(
      Long userId, CartDtos.CheckoutRequest checkoutRequest, String authorization) {
    Cart cart = cartService.getOrCreate(userId);
    return createFromCart(
        cart,
        userId,
        checkoutRequest == null ? null : checkoutRequest.cartVersion(),
        checkoutRequest == null ? null : checkoutRequest.expectedItems(),
        authorization,
        null,
        null,
        null);
  }

  @Transactional
  public CheckoutResult checkout(
      Long userId,
      CartDtos.CheckoutRequest checkoutRequest,
      String authorization,
      String idempotencyKey) {
    String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
    String requestHash = checkoutHash(checkoutRequest);
    Order existing =
        ordersRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
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
    existing = ordersRepository.findByUserIdAndIdempotencyKey(userId, normalizedKey).orElse(null);
    if (existing != null) {
      return replayOrReject(existing, requestHash);
    }

    return new CheckoutResult(
        createFromCart(
            cart,
            userId,
            checkoutRequest.cartVersion(),
            checkoutRequest.expectedItems(),
            authorization,
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
      String authorization,
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
                    CartItem::getSkuId,
                    CartItem::getQuantity,
                    Integer::sum,
                    LinkedHashMap::new));
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
                      !Objects.equals(
                          entry.getValue().quantity(), quantities.get(entry.getKey())));
      if (differentSkuSet || differentQuantity) {
        throw new IllegalStateException("CART_CHANGED");
      }
    }
    List<CatalogCheckoutItem> catalogItems;
    try {
      catalogItems =
          catalogClient.getCheckoutItems(new CatalogCheckoutRequest(skuIds), authorization);
    } catch (feign.FeignException exception) {
      if (exception.status() == 404 || exception.status() == 409) {
        throw new IllegalStateException("SKU_NOT_SELLABLE");
      }
      throw new CatalogUnavailableException();
    }
    Map<Long, CatalogCheckoutItem> catalogBySku =
        catalogItems.stream()
            .collect(Collectors.toMap(CatalogCheckoutItem::skuId, item -> item));
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
    inventoryService.reserve(quantities);

    long totalAmount =
        cart.getItems().stream()
            .mapToLong(item -> catalogBySku.get(item.getSkuId()).unitPrice() * item.getQuantity())
            .sum();
    Order order =
        new Order(userId, totalAmount, "VND", Instant.now().plusSeconds(paymentTimeoutSeconds));
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
                        item.getQuantity(),
                        catalogBySku.get(item.getSkuId()).unitPrice())));
    cartService.clear(userId, cart.getVersion());
    Order saved = ordersRepository.save(order);
    recordStatus(saved, "CHECKOUT", null);
    outboxEventService.enqueueOrderCreated(saved);
    return saved;
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
      throw new IllegalArgumentException("Idempotency-Key là bắt buộc");
    }
    if (normalizedKey.length() > 100) {
      throw new IllegalArgumentException("Idempotency-Key không được vượt quá 100 ký tự");
    }
    return normalizedKey;
  }

  private String checkoutHash(CartDtos.CheckoutRequest request) {
    String canonical =
        request.cartVersion()
            + "|"
            + request.addressId()
            + "|"
            + request.expectedItems().stream()
                .sorted(Comparator.comparing(CartDtos.CheckoutItem::skuId))
                .map(
                    item ->
                        item.skuId()
                            + ":"
                            + item.quantity()
                            + ":"
                            + item.expectedUnitPrice())
                .collect(Collectors.joining(";"));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Không thể tạo mã xác nhận checkout", exception);
    }
  }

  @Transactional
  public List<Order> listByUser(Long userId) {
    return ordersRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }

  @Transactional
  public Order getByUser(Long userId, Long orderId) {
    return ordersRepository
        .findByIdAndUserId(orderId, userId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public Order cancel(Long userId, Long orderId) {
    Order owned = getByUser(userId, orderId);
    Order order = getForUpdate(owned.getId());
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CANCELLED) return order;
    if (order.getStatus() != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      throw new IllegalStateException("INVALID_STATE_TRANSITION");
    }
    Map<Long, Integer> quantities =
        order.getItems().stream()
            .collect(
                Collectors.toMap(
                    item -> item.getSkuId(),
                    item -> item.getQuantity(),
                    Integer::sum,
                    LinkedHashMap::new));
    inventoryService.release(quantities);
    if (!order.getPaymentDueAt().isAfter(Instant.now())) order.markPaymentExpired();
    else order.cancel();
    Order saved = ordersRepository.save(order);
    recordStatus(saved, "CUSTOMER_CANCEL", saved.getCancellationReason());
    outboxEventService.enqueueOrderCancelled(saved);
    return saved;
  }

  @Transactional
  public Order handlePaymentSucceeded(PaymentEvent event) {
    Order order = getForUpdate(event.orderId());
    if (isDuplicate(event)) {
      return order;
    }
    String invalidReason = validatePayment(event, order);
    if (invalidReason != null) {
      rejectPayment(order, event, invalidReason);
      return ordersRepository.save(order);
    }
    inventoryService.finalizeReservation(quantities(order));
    order.confirm(event.paymentId());
    PaymentEventHistory history =
        new PaymentEventHistory(
            event.eventId(), event.paymentId(), event.orderId(), PaymentEventResult.ACCEPTED, "accepted");
    paymentEventHistoryRepository.save(history);
    Order saved = ordersRepository.save(order);
    recordStatus(saved, "PAYMENT_SUCCEEDED", null);
    outboxEventService.enqueueOrderConfirmed(saved);
    return saved;
  }

  @Transactional
  public Order handlePaymentFailed(PaymentEvent event) {
    Order order = getForUpdate(event.orderId());
    if (isDuplicate(event)) {
      return order;
    }
    String invalidReason = validatePayment(event, order);
    if (invalidReason != null) {
      rejectPayment(order, event, invalidReason);
      return ordersRepository.save(order);
    }
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      inventoryService.release(quantities(order));
      order.markPaymentFailed();
    }
    paymentEventHistoryRepository.save(
        new PaymentEventHistory(
            event.eventId(), event.paymentId(), event.orderId(), PaymentEventResult.FAILED,
            event.failureReason() == null ? "payment_failed" : event.failureReason()));
    Order saved = ordersRepository.save(order);
    if (saved.getStatus() == com.ecommerce.order.models.entities.OrderStatus.CANCELLED) {
      recordStatus(saved, "PAYMENT_FAILED", saved.getCancellationReason());
      outboxEventService.enqueueOrderCancelled(saved);
    }
    return saved;
  }

  @Transactional
  public int expirePendingPayments(Instant now) {
    List<Order> expired =
        ordersRepository.findExpiredPendingOrders(
            com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT, now);
    int expiredCount = 0;
    for (Order candidate : expired) {
      Order order = getForUpdate(candidate.getId());
      if (order.getStatus() != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT
          || order.getPaymentDueAt().isAfter(now)) {
        continue;
      }
      inventoryService.release(quantities(order));
      order.markPaymentExpired();
      Order saved = ordersRepository.save(order);
      recordStatus(saved, "PAYMENT_EXPIRY", saved.getCancellationReason());
      outboxEventService.enqueueOrderCancelled(saved);
      expiredCount++;
    }
    return expiredCount;
  }

  private String validatePayment(PaymentEvent event, Order order) {
    if (event.eventId() == null || event.eventId().isBlank()) return "EVENT_ID_REQUIRED";
    if (event.paymentId() == null || event.paymentId() <= 0) return "INVALID_PAYMENT_ID";
    if (order.getId() != null && !Objects.equals(event.orderId(), order.getId())) {
      return "INVALID_ORDER_ID";
    }
    if (!Objects.equals(event.amount(), order.getTotalAmount())) return "PAYMENT_AMOUNT_MISMATCH";
    if (!Objects.equals(event.currency(), order.getCurrency())) return "PAYMENT_CURRENCY_MISMATCH";
    if (!Objects.equals(event.paymentDueAt(), order.getPaymentDueAt())) return "PAYMENT_DUE_AT_MISMATCH";
    if (!order.getPaymentDueAt().isAfter(Instant.now())) return "PAYMENT_EXPIRED";
    if (order.getStatus() != com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      return "ORDER_NOT_PENDING";
    }
    return null;
  }

  private boolean isDuplicate(PaymentEvent event) {
    return (event.eventId() != null && paymentEventHistoryRepository.existsByEventId(event.eventId()))
        || (event.paymentId() != null
            && paymentEventHistoryRepository.existsByPaymentId(event.paymentId()));
  }

  private void rejectPayment(Order order, PaymentEvent event, String reason) {
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.PENDING_PAYMENT) {
      inventoryService.release(quantities(order));
      order.markPaymentReview();
      recordStatus(order, "PAYMENT_REJECTED", reason);
    }
    paymentEventHistoryRepository.save(
        new PaymentEventHistory(
            event.eventId(), event.paymentId(), event.orderId(), PaymentEventResult.REVIEW, reason));
    outboxEventService.enqueueOrderConfirmationRejected(order, event.paymentId(), rejectionReason(reason));
  }

  private String rejectionReason(String reason) {
    return switch (reason) {
      case "PAYMENT_EXPIRED" -> "PAYMENT_EXPIRED";
      case "ORDER_NOT_PENDING" -> "ORDER_CANCELLED";
      case "PAYMENT_AMOUNT_MISMATCH", "PAYMENT_CURRENCY_MISMATCH", "PAYMENT_DUE_AT_MISMATCH" ->
          "PAYMENT_MISMATCH";
      default -> "PAYMENT_MISMATCH";
    };
  }

  @Transactional
  public Order ship(Long orderId) {
    Order order = getForUpdate(orderId);
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.SHIPPING) return order;
    order.ship();
    Order saved = ordersRepository.save(order);
    recordStatus(saved, "ADMIN_SHIP", null);
    outboxEventService.enqueueOrderShipping(saved);
    return saved;
  }

  @Transactional
  public Order complete(Long orderId) {
    Order order = getForUpdate(orderId);
    if (order.getStatus() == com.ecommerce.order.models.entities.OrderStatus.COMPLETED) return order;
    order.complete();
    Order saved = ordersRepository.save(order);
    recordStatus(saved, "ADMIN_COMPLETE", null);
    outboxEventService.enqueueOrderCompleted(saved);
    return saved;
  }

  private Order getForUpdate(Long orderId) {
    if (orderId == null) {
      throw new IllegalArgumentException("ORDER_ID_REQUIRED");
    }
    return ordersRepository
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
    if (!ordersRepository.existsById(orderId)) throw new OrderNotFoundException(orderId);
    return orderStatusHistoryRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
  }

  @Transactional
  public Page<Order> listForAdmin(
      Long userId, com.ecommerce.order.models.entities.OrderStatus status,
      Instant from, Instant to, Pageable pageable) {
    return ordersRepository.findForAdmin(userId, status, from, to, pageable);
  }

  @Transactional
  public Order getForAdmin(Long orderId) {
    return ordersRepository.findById(orderId)
        .orElseThrow(() -> new OrderNotFoundException(orderId));
  }

  @Transactional
  public OrderReport report(Instant from, Instant to) {
    Map<String, Long> countByStatus = new LinkedHashMap<>();
    ordersRepository.countByStatus(from, to)
        .forEach(row -> countByStatus.put(((com.ecommerce.order.models.entities.OrderStatus) row[0]).name(),
            (Long) row[1]));
    return new OrderReport(countByStatus, ordersRepository.completedOrderValue(from, to));
  }

  private void recordStatus(Order order, String source, String reason) {
    if (orderStatusHistoryRepository != null) {
      orderStatusHistoryRepository.save(new OrderStatusHistory(order, source, reason));
    }
  }

  public record OrderReport(Map<String, Long> countByStatus, long completedOrderValue) {}
}
