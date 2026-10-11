package com.ecommerce.order.configs;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
  @Bean
  DefaultErrorHandler kafkaErrorHandler(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${spring.kafka.consumer.group-id}") String consumerGroup,
      @Value("${app.kafka.retry.interval-ms:2000}") long retryIntervalMs,
      @Value("${app.kafka.retry.max-retries:3}") long maxRetries) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) ->
                new TopicPartition(record.topic() + "." + consumerGroup + ".dlt", record.partition()));
    recoverer.setFailIfSendResultIsError(true);
    // DLT phải có cùng số partition; không chuyển sang partition khác khi thiếu cấu hình.
    recoverer.setVerifyPartition(false);
    recoverer.setHeadersFunction(
        (record, exception) ->
            new RecordHeaders()
                .add("x-dlt-consumer-group", consumerGroup.getBytes(StandardCharsets.UTF_8))
                .add("x-dlt-error-code", "CONSUMER_PROCESSING_FAILED".getBytes(StandardCharsets.UTF_8))
                .add("x-dlt-error-summary", "Không thể xử lý sự kiện".getBytes(StandardCharsets.UTF_8))
                .add("x-dlt-failed-at", Instant.now().toString().getBytes(StandardCharsets.UTF_8)));
    DefaultErrorHandler errorHandler =
        new DefaultErrorHandler(recoverer, new FixedBackOff(retryIntervalMs, maxRetries));
    errorHandler.setCommitRecovered(true);
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class, JsonProcessingException.class);
    errorHandler.setAckAfterHandle(false);
    return errorHandler;
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
      ConsumerFactory<String, String> consumerFactory, CommonErrorHandler kafkaErrorHandler) {
    ConcurrentKafkaListenerContainerFactory<String, String> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(consumerFactory);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    factory.setCommonErrorHandler(kafkaErrorHandler);
    return factory;
  }
}
