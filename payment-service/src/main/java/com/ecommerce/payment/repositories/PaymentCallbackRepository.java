package com.ecommerce.payment.repositories;
import com.ecommerce.payment.models.entities.PaymentCallback;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentCallbackRepository extends JpaRepository<PaymentCallback, Long> {
  Optional<PaymentCallback> findByProviderEventId(String providerEventId);
}
