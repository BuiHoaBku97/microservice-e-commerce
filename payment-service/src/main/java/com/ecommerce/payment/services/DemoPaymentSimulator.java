package com.ecommerce.payment.services;

import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "demo"})
public class DemoPaymentSimulator {
  private final PaymentRepository paymentRepository;
  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;
  private final byte[] callbackSecret;

  public DemoPaymentSimulator(
      PaymentRepository paymentRepository,
      PaymentService paymentService,
      ObjectMapper objectMapper,
      @Value("${app.payment.callback-secret:}") String callbackSecret) {
    this.paymentRepository = paymentRepository;
    this.paymentService = paymentService;
    this.objectMapper = objectMapper;
    this.callbackSecret = callbackSecret.getBytes(StandardCharsets.UTF_8);
  }

  public Payment simulate(Long paymentId, PaymentCallbackRequest.Result result) {
    if (callbackSecret.length == 0)
      throw new IllegalStateException("PAYMENT_CALLBACK_SECRET_NOT_CONFIGURED");
    Payment payment =
        paymentRepository
            .findById(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("PAYMENT_NOT_FOUND"));
    if (!"MOCK_ONLINE".equals(payment.getPaymentMethod())) {
      throw new IllegalStateException("PAYMENT_METHOD_UNSUPPORTED");
    }
    String eventId = "demo-" + UUID.randomUUID();
    String transactionId = "demo-tx-" + UUID.randomUUID();
    PaymentCallbackRequest callback =
        new PaymentCallbackRequest(
            eventId,
            transactionId,
            payment.getId(),
            payment.getOrderId(),
            result,
            payment.getAmount(),
            payment.getCurrency(),
            result == PaymentCallbackRequest.Result.SUCCESS ? Instant.now() : null);
    try {
      String rawBody = objectMapper.writeValueAsString(callback);
      String timestamp = Long.toString(Instant.now().getEpochSecond());
      return paymentService.callback(callback, rawBody, timestamp, sign(timestamp + rawBody));
    } catch (RuntimeException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new IllegalStateException("PAYMENT_SIMULATION_FAILED", exception);
    }
  }

  private String sign(String value) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(callbackSecret, "HmacSHA256"));
    return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
  }
}
