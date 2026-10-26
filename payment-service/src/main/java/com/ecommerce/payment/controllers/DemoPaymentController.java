package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.dtos.requests.SimulatePaymentRequest;
import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.services.DemoPaymentSimulator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/demo/payments")
public class DemoPaymentController {
  private final DemoPaymentSimulator paymentSimulator;

  public DemoPaymentController(DemoPaymentSimulator paymentSimulator) {
    this.paymentSimulator = paymentSimulator;
  }

  @PostMapping("/{id}/simulate")
  public ApiResponse<PaymentResponse> simulate(
      @PathVariable Long id,
      @Valid @RequestBody SimulatePaymentRequest simulationRequest,
      HttpServletRequest request) {
    return ApiResponse.success(
        PaymentResponse.from(paymentSimulator.simulate(id, simulationRequest.result())),
        request.getRequestId());
  }
}
