package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.services.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/mock")
public class PaymentCallbackController {
  private final PaymentService paymentService;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public PaymentCallbackController(PaymentService paymentService, ObjectMapper objectMapper,
      Validator validator) {
    this.paymentService = paymentService;
    this.objectMapper = objectMapper;
    this.validator = validator;
  }

  @PostMapping("/callback")
  public ResponseEntity<ApiResponse<PaymentResponse>> callback(
      @RequestBody String rawBody,
      @RequestHeader("X-Mock-Timestamp") String timestamp,
      @RequestHeader("X-Mock-Signature") String signature,
      HttpServletRequest request) throws Exception {
    PaymentCallbackRequest callback = objectMapper.readValue(rawBody, PaymentCallbackRequest.class);
    if (!validator.validate(callback).isEmpty()) throw new IllegalArgumentException("VALIDATION_ERROR");
    return ResponseEntity.ok(ApiResponse.success(
        PaymentResponse.from(paymentService.callback(callback, rawBody, timestamp, signature)),
        request.getRequestId()));
  }
}
