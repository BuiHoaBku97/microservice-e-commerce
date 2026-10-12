package com.ecommerce.payment.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.listener.RecordInterceptor;

/** Logs identifiers only; payloads may contain personal information. */
public class EventRecordInterceptor implements RecordInterceptor<String, String> {
  private static final Logger log = LoggerFactory.getLogger(EventRecordInterceptor.class);
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public ConsumerRecord<String, String> intercept(
      ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
    return record;
  }

  @Override
  public void success(ConsumerRecord<String, String> record, Consumer<String, String> consumer) {
    report(record, "handled");
  }

  @Override
  public void failure(ConsumerRecord<String, String> record, Exception exception,
      Consumer<String, String> consumer) {
    report(record, "failed");
  }

  private void report(ConsumerRecord<String, String> record, String outcome) {
    JsonNode payload;
    try {
      payload = mapper.readTree(record.value());
      if (payload == null) payload = mapper.createObjectNode();
    } catch (Exception exception) {
      payload = mapper.createObjectNode();
    }
    log.info("event outcome={} topic={} partition={} offset={} eventId={} orderId={} paymentId={}",
        outcome, record.topic(), record.partition(), record.offset(),
        identifier(payload, "eventId"), identifier(payload, "orderId"), identifier(payload, "paymentId"));
  }

  private String identifier(JsonNode payload, String field) {
    String value = payload.path(field).asText("");
    return value.substring(0, Math.min(value.length(), 100)).replaceAll("[\\r\\n\\t]", "_");
  }
}
