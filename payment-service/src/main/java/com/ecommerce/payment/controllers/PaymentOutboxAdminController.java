package com.ecommerce.payment.controllers;

import com.ecommerce.payment.models.entities.*;
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
  public List<OutboxEvent> list(@RequestParam(defaultValue = "FAILED") OutboxStatus status) {
    return outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(status);
  }

  @PostMapping("/{id}/replay")
  @Transactional
  public OutboxEvent replay(@PathVariable Long id) {
    OutboxEvent event =
        outboxEventRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("OUTBOX_NOT_FOUND"));
    event.replay();
    return event;
  }
}
