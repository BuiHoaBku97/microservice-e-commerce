package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import com.ecommerce.payment.models.dtos.responses.PaymentInitializationResponse;
import com.ecommerce.payment.services.PaymentInitializationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class PaymentInitializationController {
  private final PaymentInitializationService service;

  public PaymentInitializationController(PaymentInitializationService service) { this.service = service; }

  @PostMapping("/{orderId}/payment/initialize")
  public ResponseEntity<ApiResponse<PaymentInitializationResponse>> initialize(
      @PathVariable Long orderId, Authentication authentication, HttpServletRequest request) {
    var response = service.initialize(Long.valueOf(authentication.getName()), orderId,
        request.getRemoteAddr());
    return ResponseEntity.ok(ApiResponse.success(response, request.getRequestId()));
  }
}
