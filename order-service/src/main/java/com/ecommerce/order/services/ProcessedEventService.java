package com.ecommerce.order.services;

import com.ecommerce.order.repositories.ProcessedEventRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
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
    return process(
        consumerName, eventId, eventType, payloadHash(eventId + "|" + eventType), null, action);
  }

  @Transactional
  public boolean process(
      String consumerName,
      String eventId,
      String eventType,
      String payloadHash,
      Long aggregateId,
      Runnable action) {
    if (processedEventRepository.claim(consumerName, eventId, eventType, payloadHash, aggregateId)
        == 0) {
      processedEventRepository
          .findByConsumerNameAndEventId(consumerName, eventId)
          .filter(existing -> !Objects.equals(existing.getPayloadHash(), payloadHash))
          .ifPresent(
              existing -> {
                throw new IllegalStateException("EVENT_ID_PAYLOAD_CONFLICT");
              });
      return false;
    }
    action.run();
    return true;
  }

  public static String payloadHash(String payload) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("EVENT_HASH_FAILED", exception);
    }
  }
}
