package com.ecommerce.order.services;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class PaymentExpiryScheduler {
  private final OrderService orderService;

  public PaymentExpiryScheduler(OrderService orderService) {
    this.orderService = orderService;
  }

  @Scheduled(fixedDelayString = "${app.order.payment-expiry-scan-delay-ms:30000}")
  public void expirePendingPayments() {
    orderService.expirePendingPayments();
  }
}
