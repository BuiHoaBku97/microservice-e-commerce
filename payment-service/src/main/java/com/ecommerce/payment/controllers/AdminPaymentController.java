package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.models.entities.PaymentStatus;
import com.ecommerce.payment.models.dtos.responses.PaymentStatusHistoryResponse;
import com.ecommerce.payment.services.PaymentQueryService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPaymentController {
  private final PaymentQueryService paymentQueryService;

  public AdminPaymentController(PaymentQueryService paymentQueryService) {
    this.paymentQueryService = paymentQueryService;
  }

  @GetMapping("/payments")
  public ApiResponse<Page<PaymentResponse>> payments(
      @RequestParam(required = false) PaymentStatus status,
      @RequestParam(required = false) Long orderId,
      @RequestParam(required = false) Long userId,
      @RequestParam(required = false) Instant from,
      @RequestParam(required = false) Instant to,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    return ApiResponse.success(
        paymentQueryService.findForAdmin(status, orderId, userId, from, to,
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
        request.getRequestId());
  }

  @GetMapping("/payments/{id}")
  public ApiResponse<PaymentResponse> payment(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(paymentQueryService.findByIdForAdmin(id), request.getRequestId());
  }

  @GetMapping("/payments/{id}/history")
  public ApiResponse<Page<PaymentStatusHistoryResponse>> history(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    return ApiResponse.success(paymentQueryService.history(id,
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100))),
        request.getRequestId());
  }

  @GetMapping("/reports/payments")
  public ApiResponse<PaymentQueryService.PaymentReport> report(HttpServletRequest request) {
    return ApiResponse.success(paymentQueryService.report(), request.getRequestId());
  }
}
