package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.entities.*;
import com.ecommerce.payment.models.dtos.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import com.ecommerce.payment.repositories.OutboxEventRepository;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/payments/outbox")
public class PaymentOutboxAdminController {
  private final OutboxEventRepository outboxEventRepository;

  public PaymentOutboxAdminController(OutboxEventRepository outboxEventRepository) {
    this.outboxEventRepository = outboxEventRepository;
  }

  @GetMapping
  public ApiResponse<List<OutboxEvent>> list(@RequestParam(defaultValue = "FAILED") OutboxStatus status,
      HttpServletRequest request) {
    return ApiResponse.success(outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(status),
        request.getRequestId());
  }

  @PostMapping("/{id}/replay")
  @Transactional
  public ApiResponse<OutboxEvent> replay(@PathVariable Long id, HttpServletRequest request) {
    OutboxEvent event =
        outboxEventRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("OUTBOX_NOT_FOUND"));
    event.replay();
    return ApiResponse.success(event, request.getRequestId());
  }
}
