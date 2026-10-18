package com.ecommerce.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.models.entities.Reservation;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.repositories.ReservationRepository;
import com.ecommerce.order.services.OrderService;
import com.ecommerce.order.services.OutboxEventService;
import com.ecommerce.payment.messaging.events.OrderCancelledEvent;
import com.ecommerce.payment.messaging.events.OrderCreatedEvent;
import com.ecommerce.payment.models.dtos.requests.PaymentCallbackRequest;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentTransaction;
import com.ecommerce.payment.repositories.PaymentTransactionRepository;
import com.ecommerce.payment.services.VnpayCallbackService;
import com.ecommerce.payment.services.VnpayService;
import com.ecommerce.payment.services.PaymentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.HexFormat;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Tag("integration")
@Testcontainers
@Timeout(120)
class EventFlowIntegrationTest {
  private static final String ORDER_TOPIC = "c4.order.events";
  private static final String PAYMENT_TOPIC = "c4.payment.events";
  @Container static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16.4");
  @Container static final KafkaContainer KAFKA =
      new KafkaContainer(DockerImageName.parse("apache/kafka:3.9.1"));
  @Container static final GenericContainer<?> REDIS =
      new GenericContainer<>(DockerImageName.parse("redis:7.4.1")).withExposedPorts(6379);
  private static ConfigurableApplicationContext orders;
  private static ConfigurableApplicationContext payments;
  private static ConfigurableApplicationContext notifications;
  private static Admin admin;

  @BeforeAll
  static void startServices() throws Exception {
    admin = Admin.create(Map.of("bootstrap.servers", KAFKA.getBootstrapServers()));
    List<NewTopic> topics = new ArrayList<>();
    for (String topic : List.of(ORDER_TOPIC, PAYMENT_TOPIC)) {
      topics.add(new NewTopic(topic, 3, (short) 1));
      for (String service : List.of("order", "payment", "notification")) {
        topics.add(new NewTopic(topic + "." + service + "-service.dlt", 3, (short) 1));
      }
    }
    admin.createTopics(topics).all().get(30, TimeUnit.SECONDS);
    orders = start(ServiceContexts.Orders.class, "order");
    payments = start(ServiceContexts.Payments.class, "payment");
    notifications = start(ServiceContexts.Notifications.class, "notification");
  }

  private static ConfigurableApplicationContext start(Class<?> configuration, String service) {
    String schema = "c4_" + service;
    String migration = Path.of(System.getProperty("repo.root"), service + "-service",
        "src/main/resources/db/migration").toString().replace('\\', '/');
    var context = new SpringApplicationBuilder(configuration).web(WebApplicationType.NONE).run(
        "--spring.config.name=c4-isolated-test",
        "--spring.application.name=" + service + "-service",
        "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
        "--spring.datasource.username=" + POSTGRES.getUsername(),
        "--spring.datasource.password=" + POSTGRES.getPassword(),
        "--spring.datasource.hikari.connection-init-sql=SET search_path TO " + schema,
        "--spring.flyway.locations=filesystem:" + migration,
        "--spring.flyway.schemas=" + schema,
        "--spring.flyway.default-schema=" + schema,
        "--spring.jpa.hibernate.ddl-auto=validate",
        "--spring.jpa.properties.hibernate.default_schema=" + schema,
        "--spring.jackson.serialization.write-dates-as-timestamps=false",
        "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers(),
        "--spring.kafka.consumer.group-id=" + service + "-service",
        "--spring.kafka.consumer.auto-offset-reset=earliest",
        "--spring.kafka.consumer.enable-auto-commit=false",
        "--spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "--spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "--spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "--spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer",
        "--spring.kafka.producer.acks=all",
        "--spring.kafka.producer.properties.enable.idempotence=true",
        "--spring.kafka.listener.ack-mode=manual_immediate",
        "--app.kafka.topics.order-events=" + ORDER_TOPIC,
        "--app.kafka.topics.payment-events=" + PAYMENT_TOPIC,
        "--app.kafka.retry.interval-ms=100",
        "--app.kafka.retry.max-retries=3",
        "--app.payment.callback-secret=c4-test-secret",
        "--app.payment.vnpay.enabled=true",
        "--app.payment.vnpay.tmn-code=C4TMN001",
        "--app.payment.vnpay.hash-secret=c4-vnpay-secret",
        "--app.payment.vnpay.return-url=https://test.example/vnpay/return",
        "--app.payment.vnpay.ipn-url=https://test.example/vnpay/ipn",
        "--app.payment.vnpay.frontend-result-url=https://test.example/payment/result",
        "--test.redis.address=redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379));
    context.getBean(KafkaListenerEndpointRegistry.class).getListenerContainers()
        .forEach(container -> ContainerTestUtils.waitForAssignment(container, 3));
    return context;
  }

  @AfterAll
  static void stopServices() {
    if (notifications != null) notifications.close();
    if (payments != null) payments.close();
    if (orders != null) orders.close();
    if (admin != null) admin.close();
  }

  @Test
  void payment_shouldRemainClosed_whenCancellationArrivesBeforeCreation() {
    long orderId =
        9_000_000L + Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 999_999L);
    PaymentService paymentService = payments.getBean(PaymentService.class);

    paymentService.handleOrderCancelled(
        new OrderCancelledEvent(
            UUID.randomUUID().toString(),
            orderId,
            "PAYMENT_EXPIRED",
            Instant.now(),
            2L));
    paymentService.processOrderPayment(
        new OrderCreatedEvent(
            orderId, 20L, 100_000L, "VND", Instant.now().plusSeconds(300)));

    Map<String, Object> payment =
        jdbc(payments)
            .queryForMap(
                "SELECT status, funds_status, order_acceptance FROM payments WHERE order_id=?",
                orderId);
    assertThat(payment)
        .containsEntry("status", "EXPIRED")
        .containsEntry("funds_status", "NOT_RECEIVED")
        .containsEntry("order_acceptance", "REJECTED");
    assertThat(
            count(
                payments,
                "SELECT count(*) FROM outbox_events WHERE aggregate_id=?",
                orderId))
        .isZero();
  }

  @Test
  void flow_shouldConfirmAndNotifyOnce_whenReplayedAfterConsumerRestart() throws Exception {
    Order order = seed(Instant.now().plusSeconds(600));
    tx().executeWithoutResult(status ->
        orders.getBean(OutboxEventService.class).enqueueOrderCreated(
            orders.getBean(OrderRepository.class).findById(order.getId()).orElseThrow()));
    String orderPayload = jdbc(orders).queryForObject(
        "SELECT payload FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCreated'",
        String.class, order.getId());
    orders.getBean(com.ecommerce.order.messaging.publishers.OutboxPublisher.class).publishPendingEvents();
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        assertThat(count(payments, "SELECT count(*) FROM payments WHERE order_id=?", order.getId())).isEqualTo(1));
    submitSuccessfulPaymentCallback(order);
    String paymentPayload = jdbc(payments).queryForObject(
        "SELECT payload FROM outbox_events WHERE payload::jsonb -> 'payload' ->> 'orderId'=?", String.class,
        order.getId().toString());
    payments.getBean(com.ecommerce.payment.messaging.publishers.OutboxPublisher.class).publishPendingEvents();
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> assertStock(order, "CONFIRMED", 8, 0));
    orders.getBean(com.ecommerce.order.messaging.publishers.OutboxPublisher.class).publishPendingEvents();
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
        assertThat(count(notifications, "SELECT count(*) FROM notifications WHERE reference_id=?", order.getId()))
            .isEqualTo(3));
    String eventId = new ObjectMapper().readTree(paymentPayload).path("eventId").asText();
    long accepted = count(orders, "SELECT count(*) FROM payment_event_history WHERE event_id=?", eventId);
    assertThat(accepted).isEqualTo(1);

    // A new application context reuses the same group and DB; committed offsets survive restart.
    orders.close();
    orders = start(ServiceContexts.Orders.class, "order");
    var replay = template(payments).send(PAYMENT_TOPIC, order.getId().toString(), paymentPayload)
        .get(10, TimeUnit.SECONDS).getRecordMetadata();
    TopicPartition partition = new TopicPartition(PAYMENT_TOPIC, replay.partition());
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      var offset = admin.listConsumerGroupOffsets("order-service").partitionsToOffsetAndMetadata()
          .get(10, TimeUnit.SECONDS).get(partition);
      assertThat(offset).isNotNull();
      assertThat(offset.offset()).isGreaterThan(replay.offset());
    });
    assertStock(order, "CONFIRMED", 8, 0);
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE event_id=?", eventId)).isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderConfirmed'",
        order.getId())).isEqualTo(1);
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      var offset = admin.listConsumerGroupOffsets("notification-service").partitionsToOffsetAndMetadata()
          .get(10, TimeUnit.SECONDS).get(partition);
      assertThat(offset).isNotNull();
      assertThat(offset.offset()).isGreaterThan(replay.offset());
    });
    assertThat(count(notifications, "SELECT count(*) FROM notifications WHERE reference_id=?", order.getId()))
        .isEqualTo(3);
    var orderReplay = template(orders).send(ORDER_TOPIC, order.getId().toString(), orderPayload)
        .get(10, TimeUnit.SECONDS).getRecordMetadata();
    for (String group : List.of("payment-service", "notification-service")) {
      await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
        var offset = admin.listConsumerGroupOffsets(group).partitionsToOffsetAndMetadata()
            .get(10, TimeUnit.SECONDS).get(new TopicPartition(ORDER_TOPIC, orderReplay.partition()));
        assertThat(offset).isNotNull();
        assertThat(offset.offset()).isGreaterThan(orderReplay.offset());
      });
    }
    assertThat(count(payments, "SELECT count(*) FROM payments WHERE order_id=?", order.getId())).isEqualTo(1);
    assertThat(count(payments, "SELECT count(*) FROM outbox_events WHERE payload::jsonb -> 'payload' ->> 'orderId'=?",
        order.getId().toString())).isEqualTo(1);
    assertThat(count(notifications, "SELECT count(*) FROM notifications WHERE reference_id=?", order.getId()))
        .isEqualTo(3);
  }

  @Test
  void payment_shouldReleaseAndNotify_whenFailureIsReplayed() throws Exception {
    Order order = seed(Instant.now().plusSeconds(600));
    var mapper = orders.getBean(ObjectMapper.class);
    var payload = mapper.createObjectNode();
    payload.put("eventId", UUID.randomUUID().toString());
    payload.put("eventType", "PaymentFailed");
    payload.put("schemaVersion", 1);
    payload.put("aggregateType", "PAYMENT");
    payload.put("aggregateId", order.getId() + 100000);
    payload.put("aggregateVersion", 0);
    payload.put("occurredAt", Instant.now().toString());
    payload.put("correlationId", UUID.randomUUID().toString());
    var businessPayload = payload.putObject("payload");
    businessPayload.put("paymentId", order.getId() + 100000);
    businessPayload.put("orderId", order.getId());
    businessPayload.put("userId", order.getUserId());
    businessPayload.put("amount", 100);
    businessPayload.put("currency", "VND");
    businessPayload.put("failedAt", Instant.now().toString());
    businessPayload.put("reason", "provider_declined");
    for (int i = 0; i < 2; i++) {
      template(payments).send(PAYMENT_TOPIC, order.getId().toString(), payload.toString())
          .get(10, TimeUnit.SECONDS);
    }
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      assertStock(order, "CANCELLED", 10, 0);
      assertThat(count(notifications, "SELECT count(*) FROM notifications WHERE reference_id=?", order.getId()))
          .isEqualTo(1);
    });
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
        .isEqualTo(1);
  }

  @Test
  void vnpayIpn_shouldUpdatePostgresAndEnqueuePaymentEvent() throws Exception {
    Order order = seed(Instant.now().plusSeconds(600));
    Payment payment = payments.getBean(PaymentService.class).processOrderPayment(
        new OrderCreatedEvent(order.getId(), order.getUserId(), order.getTotalAmount(),
            order.getCurrency(), order.getPaymentDueAt(), "VNPAY"));
    var transaction = payments.getBean(PaymentTransactionRepository.class).saveAndFlush(
        new PaymentTransaction(payment.getId(), "VNPAY", "PAY-" + payment.getId() + "-integration",
            payment.getAmount(), payment.getCurrency()));
    Map<String, String> callback = vnpayCallback(payment, transaction);

    assertThat(payments.getBean(VnpayCallbackService.class).process(callback)).isEqualTo("00");
    assertThat(jdbc(payments).queryForObject(
        "SELECT status FROM payments WHERE id=?", String.class, payment.getId())).isEqualTo("SUCCESS");
    payments.getBean(com.ecommerce.payment.messaging.publishers.OutboxPublisher.class).publishPendingEvents();
    assertThat(count(payments, "SELECT count(*) FROM payment_transactions WHERE txn_ref=? AND status='SUCCESS'",
        transaction.getTxnRef())).isEqualTo(1);
    assertThat(count(payments,
        "SELECT count(*) FROM outbox_events WHERE payload::jsonb -> 'payload' ->> 'paymentId'=?",
        payment.getId().toString())).isEqualTo(1);
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void payment_shouldFinalizeOnce_whenCallbacksRaceInSeparateTransactions(boolean distinctEventIds)
      throws Exception {
    Order order = seed(Instant.now().plusSeconds(600));
    PaymentEvent event = event(order, order.getTotalAmount());
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var tasks = new ArrayList<java.util.concurrent.Future<?>>();
      for (int i = 0; i < 2; i++) {
        PaymentEvent callback = distinctEventIds && i == 1
            ? new PaymentEvent(UUID.randomUUID().toString(), event.eventType(), event.paymentId(),
                event.orderId(), event.amount(), event.currency(), null)
            : event;
        tasks.add(pool.submit(() -> {
          ready.countDown();
          assertThat(go.await(10, TimeUnit.SECONDS)).isTrue();
          orders.getBean(OrderService.class).handlePaymentSucceeded(callback);
          return null;
        }));
      }
      try {
        assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
      } finally {
        go.countDown();
      }
      for (var task : tasks) task.get(30, TimeUnit.SECONDS);
    }
    assertStock(order, "CONFIRMED", 8, 0);
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
        .isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=?", order.getId())).isEqualTo(1);
  }

  @Test
  void payment_shouldCancelWithoutFinalizing_whenLateOrWrongAmount() {
    for (boolean late : List.of(true, false)) {
      Order order = seed(late ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(600));
      PaymentEvent event = event(order, late ? 100L : 99L);
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      assertStock(order, "CANCELLED", 10, 0);
      assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
          .isEqualTo(1);
      assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderConfirmationRejected'",
          order.getId())).isEqualTo(1);
      assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCancelled'",
          order.getId())).isEqualTo(late ? 1 : 0);
    }
  }

  @Test
  void payment_shouldNotReleaseTwice_whenCallbackArrivesAfterExpiry() {
    Order order = seed(Instant.now().minusSeconds(60));
    orders.getBean(OrderService.class).expirePendingPayments();
    PaymentEvent event = event(order, 100L);
    orders.getBean(OrderService.class).handlePaymentSucceeded(event);
    orders.getBean(OrderService.class).handlePaymentSucceeded(event);
    assertStock(order, "CANCELLED", 10, 0);
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
        .isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCancelled'",
        order.getId())).isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderConfirmed'",
        order.getId())).isZero();
  }

  @Test
  void payment_shouldExpire_whenTransactionWaitsOnOrderLockPastDeadline() throws Exception {
    Instant due =
        jdbc(orders)
            .queryForObject(
                "SELECT clock_timestamp() + interval '2 seconds'",
                (resultSet, rowNumber) -> resultSet.getTimestamp(1).toInstant());
    Order order = seed(due);
    CountDownLatch orderLocked = new CountDownLatch(1);
    CountDownLatch releaseLock = new CountDownLatch(1);
    var executor = Executors.newFixedThreadPool(2);

    try {
      var lockHolder =
          executor.submit(
              () ->
                  tx()
                      .executeWithoutResult(
                          status -> {
                            orders
                                .getBean(OrderRepository.class)
                                .findByIdForUpdate(order.getId())
                                .orElseThrow();
                            orderLocked.countDown();
                            try {
                              if (!releaseLock.await(10, TimeUnit.SECONDS)) {
                                throw new IllegalStateException("Timed out while holding order lock");
                              }
                            } catch (InterruptedException exception) {
                              Thread.currentThread().interrupt();
                              throw new IllegalStateException(exception);
                            }
                          }));
      assertThat(orderLocked.await(10, TimeUnit.SECONDS)).isTrue();

      var paymentResult =
          executor.submit(
              () -> orders.getBean(OrderService.class).handlePaymentSucceeded(event(order, 100L)));
      Thread.sleep(2_500);
      releaseLock.countDown();
      lockHolder.get(10, TimeUnit.SECONDS);
      Order result = paymentResult.get(10, TimeUnit.SECONDS);

      assertThat(result.getStatus().name()).isEqualTo("CANCELLED");
      assertThat(result.getCancellationReason()).isEqualTo("PAYMENT_EXPIRED");
      assertStock(order, "CANCELLED", 10, 0);
    } finally {
      releaseLock.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void payment_shouldRollbackInventoryAndHistory_whenTransactionFails() {
    Order order = seed(Instant.now().plusSeconds(600));
    PaymentEvent event = event(order, 100L);
    assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      throw new IllegalStateException("simulate crash before commit");
    })).isInstanceOf(IllegalStateException.class);
    assertStock(order, "PENDING_PAYMENT", 10, 2);
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId())).isZero();
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=?", order.getId())).isZero();
    orders.getBean(OrderService.class).handlePaymentSucceeded(event);
    assertStock(order, "CONFIRMED", 8, 0);
  }

  @Test
  void dlt_shouldPreserveRecordAndCommitSourceOffset_whenEnvelopeIsInvalid() throws Exception {
    String payload = "{\"eventId\":\"" + UUID.randomUUID() + "\",\"eventType\":\"Unknown\"}";
    String dlt = PAYMENT_TOPIC + ".order-service.dlt";
    try (var consumer = new KafkaConsumer<String, String>(Map.of(
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
        ConsumerConfig.GROUP_ID_CONFIG, "c4-dlt-" + UUID.randomUUID(),
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
        ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class))) {
      consumer.subscribe(List.of(dlt));
      var sent = template(payments).send(PAYMENT_TOPIC, "999", payload).get(10, TimeUnit.SECONDS).getRecordMetadata();
      var received = new ArrayList<org.apache.kafka.clients.consumer.ConsumerRecord<String, String>>();
      await().atMost(Duration.ofSeconds(30)).until(() -> {
        consumer.poll(Duration.ofMillis(200)).forEach(record -> {
          if (payload.equals(record.value())) received.add(record);
        });
        return !received.isEmpty();
      });
      assertThat(received.getFirst().key()).isEqualTo("999");
      assertThat(received.getFirst().partition()).isEqualTo(sent.partition());
      assertThat(received.getFirst().value()).isEqualTo(payload);
      assertThat(received.getFirst().headers().lastHeader("x-dlt-consumer-group")).isNotNull();
      await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
        var offset = admin.listConsumerGroupOffsets("order-service").partitionsToOffsetAndMetadata()
            .get(10, TimeUnit.SECONDS).get(new TopicPartition(PAYMENT_TOPIC, sent.partition()));
        assertThat(offset).isNotNull();
        assertThat(offset.offset()).isGreaterThan(sent.offset());
      });
    }
  }

  private Order seed(Instant due) {
    return tx().execute(status -> {
      Order order = new Order(1L, 100L, "VND", due.truncatedTo(ChronoUnit.MICROS));
      long sku = Math.abs(UUID.randomUUID().getMostSignificantBits());
      order.addItem(new OrderItem(sku, 2, 50L));
      jdbc(orders).update("INSERT INTO inventory(sku_id,on_hand,reserved,version,created_at,updated_at)"
          + " VALUES (?,10,2,0,now(),now())", sku);
      Order saved = orders.getBean(OrderRepository.class).saveAndFlush(order);
      Reservation reservation = new Reservation(saved.getId(), due);
      reservation.addItem(sku, 2);
      orders.getBean(ReservationRepository.class).saveAndFlush(reservation);
      return saved;
    });
  }

  private PaymentEvent event(Order order, long amount) {
    return new PaymentEvent(UUID.randomUUID().toString(), "PaymentSucceeded",
        order.getId() + 100000, order.getId(), amount, "VND", null);
  }

  private void submitSuccessfulPaymentCallback(Order order) throws Exception {
    Long paymentId = jdbc(payments).queryForObject(
        "SELECT id FROM payments WHERE order_id=?", Long.class, order.getId());
    var callback = new PaymentCallbackRequest(
        "provider-event-" + order.getId(),
        "provider-transaction-" + order.getId(),
        paymentId,
        order.getId(),
        PaymentCallbackRequest.Result.SUCCESS,
        order.getTotalAmount(),
        order.getCurrency(),
        Instant.now());
    String rawBody = payments.getBean(ObjectMapper.class).writeValueAsString(callback);
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("c4-test-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    String signature = HexFormat.of().formatHex(
        mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8)));
    payments.getBean(PaymentService.class).callback(callback, rawBody, timestamp, signature);
  }

  private Map<String, String> vnpayCallback(Payment payment, PaymentTransaction transaction) throws Exception {
    Map<String, String> params = new TreeMap<>();
    params.put("vnp_Amount", Long.toString(payment.getAmount() * 100));
    params.put("vnp_BankCode", "NCB");
    params.put("vnp_BankTranNo", "BANK-" + payment.getId());
    params.put("vnp_PayDate", java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
        .withZone(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(Instant.now()));
    params.put("vnp_ResponseCode", "00");
    params.put("vnp_TmnCode", "C4TMN001");
    params.put("vnp_TransactionNo", "VNPAY-" + payment.getId());
    params.put("vnp_TransactionStatus", "00");
    params.put("vnp_TxnRef", transaction.getTxnRef());
    params.put("vnp_SecureHash", signVnpay(params));
    return params;
  }

  private String signVnpay(Map<String, String> params) throws Exception {
    String data = params.entrySet().stream().map(entry ->
        URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
            + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
        .reduce((left, right) -> left + "&" + right).orElse("");
    Mac mac = Mac.getInstance("HmacSHA512");
    mac.init(new SecretKeySpec("c4-vnpay-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
    return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
  }

  private void assertStock(Order order, String status, int onHand, int reserved) {
    assertThat(jdbc(orders).queryForObject("SELECT status FROM orders WHERE id=?", String.class, order.getId()))
        .isEqualTo(status);
    var inventory = jdbc(orders).queryForMap("SELECT on_hand,reserved FROM inventory WHERE sku_id=?",
        order.getItems().getFirst().getSkuId());
    assertThat(inventory.get("on_hand")).isEqualTo(onHand);
    assertThat(inventory.get("reserved")).isEqualTo(reserved);
  }

  private TransactionTemplate tx() {
    return new TransactionTemplate(orders.getBean(PlatformTransactionManager.class));
  }
  private static JdbcTemplate jdbc(ConfigurableApplicationContext context) {
    return context.getBean(JdbcTemplate.class);
  }
  private static long count(ConfigurableApplicationContext context, String sql, Object id) {
    return jdbc(context).queryForObject(sql, Long.class, id);
  }
  @SuppressWarnings("unchecked")
  private static KafkaTemplate<String, String> template(ConfigurableApplicationContext context) {
    return context.getBean(KafkaTemplate.class);
  }
}
