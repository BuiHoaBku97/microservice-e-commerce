package com.ecommerce.payment.services;

import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.models.entities.PaymentTransaction;
import com.ecommerce.payment.models.entities.PaymentStatusHistory;
import com.ecommerce.payment.repositories.PaymentRepository;
import com.ecommerce.payment.repositories.PaymentStatusHistoryRepository;
import com.ecommerce.payment.repositories.PaymentTransactionRepository;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VnpayCallbackService {
  private static final Logger log = LoggerFactory.getLogger(VnpayCallbackService.class);
  private static final DateTimeFormatter PAY_DATE = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
  private final VnpayService vnpayService;
  private final PaymentTransactionRepository transactionRepository;
  private final PaymentRepository paymentRepository;
  private final PaymentStatusHistoryRepository historyRepository;
  private final PaymentEventService paymentEventService;

  public VnpayCallbackService(VnpayService vnpayService,
      PaymentTransactionRepository transactionRepository, PaymentRepository paymentRepository,
      PaymentStatusHistoryRepository historyRepository, PaymentEventService paymentEventService) {
    this.vnpayService = vnpayService; this.transactionRepository = transactionRepository;
    this.paymentRepository = paymentRepository; this.historyRepository = historyRepository;
    this.paymentEventService = paymentEventService;
  }

  @Transactional
  public String process(Map<String, String> params) {
    if (!vnpayService.validSignature(params)) return "97";
    if (!vnpayService.tmnCode().equals(params.get("vnp_TmnCode"))) return "97";
    String txnRef = params.get("vnp_TxnRef");
    PaymentTransaction transaction = txnRef == null ? null
        : transactionRepository.findByTxnRefForUpdate(txnRef).orElse(null);
    if (transaction == null) return "01";
    long signedAmount;
    try { signedAmount = Long.parseLong(params.get("vnp_Amount")); }
    catch (Exception e) { return "04"; }
    if (signedAmount != transaction.getAmount() * 100L) return "04";
    if (transaction.getStatus() != PaymentTransaction.Status.CREATED) return "00";

    Payment payment = paymentRepository.findByIdForUpdate(transaction.getPaymentId()).orElse(null);
    if (payment == null || !"VNPAY".equals(payment.getPaymentMethod())) return "01";
    String responseCode = params.getOrDefault("vnp_ResponseCode", "99");
    String transactionStatus = params.getOrDefault("vnp_TransactionStatus", "99");
    String providerTransactionId = params.get("vnp_TransactionNo");
    boolean success = "00".equals(responseCode) && "00".equals(transactionStatus);
    if (success) {
      transaction.succeed(providerTransactionId, responseCode, transactionStatus,
          params.get("vnp_BankCode"), params.get("vnp_BankTranNo"), parsePayDate(params.get("vnp_PayDate")));
      boolean received = payment.receive(providerTransactionId, transaction.getPaidAt());
      if (received) {
        historyRepository.save(new PaymentStatusHistory(payment, "VNPAY_IPN", "SUCCESS"));
        paymentEventService.enqueuePaymentResult(payment);
      }
    } else {
      transaction.fail(providerTransactionId, responseCode, transactionStatus,
          params.get("vnp_BankCode"), params.get("vnp_BankTranNo"));
      if (payment.getStatus() == PaymentStatus.PENDING) {
        payment.fail("VNPAY_" + responseCode);
        historyRepository.save(new PaymentStatusHistory(payment, "VNPAY_IPN", "VNPAY_" + responseCode));
        paymentEventService.enqueuePaymentResult(payment);
      }
    }
    paymentRepository.save(payment);
    log.info("VNPay IPN processed orderId={} paymentId={} txnRef={} responseCode={} transactionStatus={}",
        payment.getOrderId(), payment.getId(), txnRef, responseCode, transactionStatus);
    return "00";
  }

  private Instant parsePayDate(String value) {
    if (value == null || value.isBlank()) return Instant.now();
    try { return LocalDateTime.parse(value, PAY_DATE).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant(); }
    catch (Exception e) { return Instant.now(); }
  }
}
