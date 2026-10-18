package com.ecommerce.payment.repositories;

import com.ecommerce.payment.models.entities.PaymentTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Long> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select transaction from PaymentTransaction transaction where transaction.txnRef = :txnRef")
  Optional<PaymentTransaction> findByTxnRefForUpdate(@Param("txnRef") String txnRef);

  Optional<PaymentTransaction> findFirstByPaymentIdAndProviderAndStatusOrderByCreatedAtDesc(
      Long paymentId, String provider, PaymentTransaction.Status status);
}
