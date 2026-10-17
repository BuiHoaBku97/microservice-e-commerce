package com.ecommerce.payment.services;

import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.OrderPaymentContext;
import com.ecommerce.payment.repositories.OrderPaymentContextRepository;
import com.ecommerce.payment.repositories.PaymentRepository;
import jakarta.transaction.Transactional;
import java.util.Optional;
import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.models.entities.PaymentCallback;
import com.ecommerce.payment.repositories.PaymentCallbackRepository;
import com.ecommerce.payment.repositories.PaymentStatusHistoryRepository;
import com.ecommerce.payment.models.entities.PaymentStatusHistory;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.models.entities.FundsStatus;
import com.ecommerce.payment.models.entities.OrderAcceptance;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {
  private final PaymentRepository paymentRepository;
  private final PaymentEventService paymentEventService;
  private final PaymentCallbackRepository callbackRepository;
  private final PaymentStatusHistoryRepository historyRepository;
  private final OrderPaymentContextRepository orderPaymentContextRepository;
  private final byte[] callbackSecret;
  private final long callbackWindowSeconds;

  public PaymentService(
      PaymentRepository paymentRepository, PaymentEventService paymentEventService,
      PaymentCallbackRepository callbackRepository,
      PaymentStatusHistoryRepository historyRepository,
      OrderPaymentContextRepository orderPaymentContextRepository,
      @Value("${app.payment.callback-secret:}") String callbackSecret,
      @Value("${app.payment.callback-window-seconds:300}") long callbackWindowSeconds) {
    this.paymentRepository = paymentRepository;
    this.paymentEventService = paymentEventService;
    this.callbackRepository = callbackRepository;
    this.historyRepository = historyRepository;
    this.orderPaymentContextRepository = orderPaymentContextRepository;
    this.callbackSecret = callbackSecret.getBytes(StandardCharsets.UTF_8);
    this.callbackWindowSeconds = callbackWindowSeconds;
  }

  @Transactional
  public Payment processOrderPayment(OrderCreatedEvent order) {
    validateOrderCreated(order);
    OrderPaymentContext context = ensureAndLockContext(order.orderId());
    Optional<Payment> existing = paymentRepository.findByOrderIdForUpdate(order.orderId());
    if (existing.isPresent()) {
      return existing.get();
    }

    Payment payment =
        context.isClosed()
            ? Payment.closedFromOrder(
                order.orderId(),
                order.userId(),
                order.totalAmount(),
                order.currency(),
                order.paymentDueAt(),
                context.getCancellationReason())
            : new Payment(
                order.orderId(),
                order.userId(),
                order.totalAmount(),
                order.currency(),
                order.paymentDueAt());
    Payment saved = paymentRepository.save(payment);
    historyRepository.save(
        new PaymentStatusHistory(
            saved,
            context.isClosed() ? "ORDER_CREATED_AFTER_CANCELLATION" : "ORDER_CREATED",
            context.isClosed() ? context.getCancellationReason() : saved.getFailureReason()));
    return saved;
  }

  private void validateOrderCreated(OrderCreatedEvent order) {
    if (order.orderId() == null || order.orderId() <= 0
        || order.userId() == null || order.userId() <= 0
        || order.totalAmount() == null || order.totalAmount() <= 0
        || order.currency() == null || order.currency().isBlank()
        || order.paymentDueAt() == null) {
      throw new IllegalArgumentException("ORDER_CREATED_INVALID");
    }
  }

  @Transactional
  public Payment callback(PaymentCallbackRequest request, String rawBody, String timestamp, String signature) {
    verifySignature(rawBody, timestamp, signature);
    String hash = sha256(rawBody);
    var duplicate = callbackRepository.findByProviderEventId(request.providerEventId());
    if (duplicate.isPresent()) {
      if (!duplicate.get().getPayloadHash().equals(hash)) throw new IllegalStateException("CALLBACK_CONFLICT");
      return paymentRepository.findById(request.paymentId()).orElseThrow();
    }
    Payment payment = paymentRepository.findByIdForUpdate(request.paymentId())
        .orElseThrow(() -> new IllegalArgumentException("PAYMENT_NOT_FOUND"));
    if (!payment.getOrderId().equals(request.orderId()) || !payment.getAmount().equals(request.amount())
        || !payment.getCurrency().equals(request.currency())) throw new IllegalStateException("CALLBACK_MISMATCH");
    callbackRepository.save(new PaymentCallback(request.providerEventId(), hash, payment.getId()));
    if (request.result() == PaymentCallbackRequest.Result.SUCCESS) {
      boolean receivedNow = payment.receive(request.providerTransactionId(), request.paidAt());
      if (receivedNow) {
        paymentEventService.enqueuePaymentResult(payment);
        historyRepository.save(new PaymentStatusHistory(payment, "PROVIDER_CALLBACK", "SUCCESS"));
      }
    } else if (payment.getFundsStatus() == com.ecommerce.payment.models.entities.FundsStatus.NOT_RECEIVED) {
      payment.fail("PROVIDER_FAILED");
      paymentEventService.enqueuePaymentResult(payment);
      historyRepository.save(new PaymentStatusHistory(payment, "PROVIDER_CALLBACK", "PROVIDER_FAILED"));
    }
    return paymentRepository.save(payment);
  }

  private void verifySignature(String body, String timestamp, String signature) {
    if (callbackSecret.length == 0 || timestamp == null || signature == null) throw new SecurityException("CALLBACK_UNAUTHENTICATED");
    long seconds;
    try { seconds = Long.parseLong(timestamp); } catch (NumberFormatException e) { throw new SecurityException("CALLBACK_UNAUTHENTICATED"); }
    if (Math.abs(Instant.now().getEpochSecond() - seconds) > callbackWindowSeconds) throw new SecurityException("CALLBACK_UNAUTHENTICATED");
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(callbackSecret, "HmacSHA256"));
      byte[] expected = mac.doFinal((timestamp + body).getBytes(StandardCharsets.UTF_8));
      byte[] actual = HexFormat.of().parseHex(signature);
      if (!MessageDigest.isEqual(expected, actual)) throw new SecurityException("CALLBACK_UNAUTHENTICATED");
    } catch (SecurityException e) { throw e; } catch (Exception e) { throw new SecurityException("CALLBACK_UNAUTHENTICATED", e); }
  }

  private String sha256(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (Exception e) { throw new IllegalStateException(e); }
  }

  @Transactional
  public void handleOrderConfirmed(Long orderId, Long paymentId) {
    paymentRepository.findByOrderIdForUpdate(orderId).ifPresent(payment -> {
      if (paymentId != null && paymentId.equals(payment.getId())) {
        State before = State.of(payment);
        payment.acceptOrder();
        recordIfChanged(payment, before, "ORDER_CONFIRMED", null);
      }
    });
  }

  @Transactional
  public void handleOrderCancelled(OrderCancelledEvent event) {
    OrderPaymentContext context = ensureAndLockContext(event.orderId());
    context.close(event);
    orderPaymentContextRepository.save(context);
    paymentRepository.findByOrderIdForUpdate(event.orderId())
        .ifPresent(payment -> {
          State before = State.of(payment);
          payment.cancelFromOrder(event.reason());
          recordIfChanged(payment, before, "ORDER_CANCELLED", event.reason());
        });
  }

  @Transactional
  public void handleOrderConfirmationRejected(Long orderId, Long paymentId, String reason) {
    paymentRepository.findByOrderIdForUpdate(orderId).ifPresent(payment -> {
      if (paymentId != null && paymentId.equals(payment.getId())) {
        State before = State.of(payment);
        payment.rejectOrder(reason);
        recordIfChanged(payment, before, "ORDER_CONFIRMATION_REJECTED", reason);
      }
    });
  }

  private void recordIfChanged(Payment payment, State before, String source, String reason) {
    if (!before.equals(State.of(payment))) {
      historyRepository.save(new PaymentStatusHistory(payment, source, reason));
    }
  }

  private OrderPaymentContext ensureAndLockContext(Long orderId) {
    orderPaymentContextRepository.ensureExists(orderId);
    return orderPaymentContextRepository
        .findByOrderIdForUpdate(orderId)
        .orElseThrow(() -> new IllegalStateException("ORDER_PAYMENT_CONTEXT_NOT_FOUND"));
  }

  private record State(PaymentStatus status, FundsStatus fundsStatus, OrderAcceptance acceptance) {
    static State of(Payment payment) {
      return new State(payment.getStatus(), payment.getFundsStatus(), payment.getOrderAcceptance());
    }
  }
}
