package com.ecommerce.payment.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;

@SuppressWarnings({"unchecked", "rawtypes"})
class KafkaErrorHandlerTest {
  private final KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
  private final Consumer<String, String> consumer = mock(Consumer.class);
  private final MessageListenerContainer container = mock(MessageListenerContainer.class);
  private final TopicPartition sourcePartition = new TopicPartition("source.events", 1);
  private final ConsumerRecord<String, String> record =
      new ConsumerRecord<>("source.events", 1, 7L, "10", "{\"eventId\":\"stable-id\"}");
  private DefaultErrorHandler handler;

  @BeforeEach
  void setUp() {
    ContainerProperties properties = new ContainerProperties("source.events");
    properties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    properties.setSyncCommitTimeout(Duration.ofSeconds(5));
    when(container.getContainerProperties()).thenReturn(properties);
    when(container.isRunning()).thenReturn(true);
    handler = new KafkaConsumerConfig().kafkaErrorHandler(template, "payment-service", 0L, 3L);
  }

  @Test
  void shouldRetryThreeTimesThenPublishDltAndCommitNextOffset() {
    succeedSend();
    Exception failure = new IllegalStateException("Database tạm thời không khả dụng");
    exhaustRetries(failure);
    handler.handleRemaining(failure, List.of(record), consumer, container);

    ArgumentCaptor<ProducerRecord<String, String>> sent = ArgumentCaptor.forClass(ProducerRecord.class);
    var calls = inOrder(template, consumer);
    calls.verify(template).send(sent.capture());
    calls.verify(consumer).commitSync(
        java.util.Map.of(sourcePartition, new OffsetAndMetadata(8L)), Duration.ofSeconds(5));

    ProducerRecord<String, String> dltRecord = sent.getValue();
    assertThat(dltRecord.topic()).isEqualTo("source.events.payment-service.dlt");
    assertThat(dltRecord.partition()).isEqualTo(1);
    assertThat(dltRecord.key()).isEqualTo(record.key());
    assertThat(dltRecord.value()).isEqualTo(record.value());
    assertThat(header(dltRecord, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(record.topic());
    assertThat(header(dltRecord, "x-dlt-consumer-group")).isEqualTo("payment-service");
    assertThat(header(dltRecord, "x-dlt-error-code")).isEqualTo("CONSUMER_PROCESSING_FAILED");
    assertThat(header(dltRecord, "x-dlt-failed-at")).isNotBlank();
  }

  @Test
  void shouldSeekOriginalOffsetAndNeverCommitWhenDltFutureFails() {
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker không khả dụng")));
    Exception failure = new IllegalStateException("Xử lý thất bại");
    exhaustRetries(failure);
    assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Record in retry and not yet recovered");
    verify(consumer, atLeastOnce()).seek(sourcePartition, 7L);
    verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
    verify(consumer, never()).commitAsync(anyMap(), any());
  }

  @Test
  void shouldNotCommitWhenDltSendThrowsSynchronously() {
    when(template.send(any(ProducerRecord.class)))
        .thenThrow(new IllegalStateException("Không thể gửi DLT"));
    Exception failure = new IllegalStateException("Xử lý thất bại");
    exhaustRetries(failure);
    assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
        .isInstanceOf(RuntimeException.class)
        .hasMessage("Record in retry and not yet recovered");
    verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
    verify(consumer, atLeastOnce()).seek(sourcePartition, 7L);
  }

  @Test
  void shouldSendPermanentWrappedErrorToDltWithoutRetry() {
    succeedSend();
    handler.handleRemaining(
        new IllegalStateException("Listener lỗi", new IllegalArgumentException("Event không hỗ trợ")),
        List.of(record), consumer, container);
    verify(template).send(any(ProducerRecord.class));
    verify(consumer).commitSync(
        java.util.Map.of(sourcePartition, new OffsetAndMetadata(8L)), Duration.ofSeconds(5));
    verify(consumer, never()).seek(any(TopicPartition.class), anyLong());
  }

  private void exhaustRetries(Exception failure) {
    for (int attempt = 0; attempt < 3; attempt++) {
      assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
          .isInstanceOf(RuntimeException.class)
          .hasMessage("Record in retry and not yet recovered");
    }
    verify(template, never()).send(any(ProducerRecord.class));
    verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
  }

  @Test
  void shouldWaitForBrokerConfirmationBeforeCommittingRecoveredOffset() throws Exception {
    CompletableFuture<SendResult<String, String>> sendResult = new CompletableFuture<>();
    CountDownLatch sendStarted = new CountDownLatch(1);
    when(template.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
      sendStarted.countDown();
      return sendResult;
    });
    CompletableFuture<Void> handling = CompletableFuture.runAsync(
        () -> handler.handleRemaining(new IllegalArgumentException("Event không hợp lệ"),
            List.of(record), consumer, container));
    try {
      assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();
      assertThat(handling.isDone()).isFalse();
      verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
      sendResult.complete(new SendResult<>(
          new ProducerRecord<>("source.events.payment-service.dlt", record.key(), record.value()),
          mock(RecordMetadata.class)));
      handling.get(5, TimeUnit.SECONDS);
      verify(consumer).commitSync(
          java.util.Map.of(sourcePartition, new OffsetAndMetadata(8L)), Duration.ofSeconds(5));
    } finally {
      sendResult.completeExceptionally(new IllegalStateException("Kết thúc kiểm thử"));
    }
  }

  @Test
  void shouldRecoverSameRecordAfterDltBecomesAvailableAgain() {
    when(template.send(any(ProducerRecord.class)))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("Broker ngừng")));
    Exception failure = new IllegalArgumentException("Event không hợp lệ");
    assertThatThrownBy(() -> handler.handleRemaining(failure, List.of(record), consumer, container))
        .hasMessage("Record in retry and not yet recovered");
    verify(consumer, never()).commitSync(anyMap(), any(Duration.class));
    succeedSend();
    handler.handleRemaining(failure, List.of(record), consumer, container);
    verify(consumer).commitSync(
        java.util.Map.of(sourcePartition, new OffsetAndMetadata(8L)), Duration.ofSeconds(5));
  }

  private void succeedSend() {
    when(template.send(any(ProducerRecord.class)))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(
            new SendResult<>(invocation.getArgument(0), mock(RecordMetadata.class))));
  }

  private String header(ProducerRecord<String, String> sent, String name) {
    return new String(sent.headers().lastHeader(name).value(), StandardCharsets.UTF_8);
  }
}
