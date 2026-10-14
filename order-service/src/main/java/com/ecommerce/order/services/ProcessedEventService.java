package com.ecommerce.order.services;

import com.ecommerce.order.repositories.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedEventService {
  private final ProcessedEventRepository processedEventRepository;

  public ProcessedEventService(ProcessedEventRepository processedEventRepository) {
    this.processedEventRepository = processedEventRepository;
  }

  @Transactional
  public boolean process(String consumerName, String eventId, String eventType, Runnable action) {
    if (processedEventRepository.claim(consumerName, eventId, eventType) == 0) {
      return false;
    }
    action.run();
    return true;
  }
}
