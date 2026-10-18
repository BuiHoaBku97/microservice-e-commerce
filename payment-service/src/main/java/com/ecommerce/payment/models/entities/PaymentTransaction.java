package com.ecommerce.payment.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment_transactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentTransaction {
  public enum Status { CREATED, SUCCESS, FAILED }

  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  @Column(nullable = false) private Long paymentId;
  @Column(nullable = false, length = 30) private String provider;
  @Column(nullable = false, unique = true, length = 100) private String txnRef;
  @Column(nullable = false) private Long amount;
  @Column(nullable = false, length = 3) private String currency;
  @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
  private Status status = Status.CREATED;
  @Column(length = 120) private String providerTransactionId;
  @Column(length = 10) private String responseCode;
  @Column(length = 10) private String transactionStatus;
  @Column(length = 30) private String bankCode;
  @Column(length = 120) private String bankTransactionNo;
  private Instant paidAt;
  @Column(nullable = false) private Instant createdAt;
  @Column(nullable = false) private Instant updatedAt;

  public PaymentTransaction(Long paymentId, String provider, String txnRef, Long amount,
      String currency) {
    this.paymentId = paymentId;
    this.provider = provider;
    this.txnRef = txnRef;
    this.amount = amount;
    this.currency = currency;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void succeed(String providerTransactionId, String responseCode, String transactionStatus,
      String bankCode, String bankTransactionNo, Instant paidAt) {
    this.status = Status.SUCCESS;
    this.providerTransactionId = providerTransactionId;
    this.responseCode = responseCode;
    this.transactionStatus = transactionStatus;
    this.bankCode = bankCode;
    this.bankTransactionNo = bankTransactionNo;
    this.paidAt = paidAt;
    this.updatedAt = Instant.now();
  }

  public void fail(String providerTransactionId, String responseCode, String transactionStatus,
      String bankCode, String bankTransactionNo) {
    this.status = Status.FAILED;
    this.providerTransactionId = providerTransactionId;
    this.responseCode = responseCode;
    this.transactionStatus = transactionStatus;
    this.bankCode = bankCode;
    this.bankTransactionNo = bankTransactionNo;
    this.updatedAt = Instant.now();
  }
}
