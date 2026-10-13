package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import com.ecommerce.payment.services.PaymentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class PaymentController {
  private final PaymentQueryService paymentQueryService;

  public PaymentController(PaymentQueryService paymentQueryService) {
    this.paymentQueryService = paymentQueryService;
  }

  @GetMapping("/{orderId}/payment")
  public ResponseEntity<ApiResponse<Object>> byOrder(
      @PathVariable Long orderId, Authentication authentication, HttpServletRequest request) {
    var payment = paymentQueryService.findByOrder(Long.valueOf(authentication.getName()), orderId);
    return ResponseEntity.ok(ApiResponse.success(payment, request.getRequestId()));
  }

}
