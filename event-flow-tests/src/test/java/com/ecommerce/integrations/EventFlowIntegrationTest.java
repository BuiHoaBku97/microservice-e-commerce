package com.ecommerce.integrations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.ecommerce.order.messaging.events.PaymentEvent;
import com.ecommerce.order.models.entities.Order;
import com.ecommerce.order.models.entities.OrderItem;
import com.ecommerce.order.repositories.OrderRepository;
import com.ecommerce.order.services.OrderService;
import com.ecommerce.order.services.OutboxEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    String paymentPayload = jdbc(payments).queryForObject(
        "SELECT payload FROM outbox_events WHERE payload::jsonb ->> 'orderId'=?", String.class,
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
    assertThat(count(payments, "SELECT count(*) FROM outbox_events WHERE payload::jsonb ->> 'orderId'=?",
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
    payload.put("aggregateType", "Payment");
    payload.put("aggregateId", order.getId() + 100000);
    payload.put("paymentId", order.getId() + 100000);
    payload.put("orderId", order.getId());
    payload.put("userId", order.getUserId());
    payload.put("amount", 100);
    payload.put("currency", "VND");
    payload.put("paymentDueAt", order.getPaymentDueAt().toString());
    payload.put("occurredAt", Instant.now().toString());
    payload.put("failureReason", "provider_declined");
    for (int i = 0; i < 2; i++) {
      template(payments).send(PAYMENT_TOPIC, order.getId().toString(), payload.toString())
          .get(10, TimeUnit.SECONDS);
    }
    await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
      assertStock(order, "PAYMENT_FAILED", 10, 0);
      assertThat(count(notifications, "SELECT count(*) FROM notifications WHERE reference_id=?", order.getId()))
          .isEqualTo(1);
    });
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
        .isEqualTo(1);
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
                event.orderId(), event.amount(), event.currency(), event.paymentDueAt(), null)
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
  void payment_shouldReviewWithoutFinalizing_whenLateOrWrongAmount() {
    for (boolean late : List.of(true, false)) {
      Order order = seed(late ? Instant.now().minusSeconds(60) : Instant.now().plusSeconds(600));
      PaymentEvent event = event(order, late ? 100L : 99L);
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      assertStock(order, "PAYMENT_REVIEW", 10, 0);
      assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
          .isEqualTo(1);
      assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=?", order.getId())).isZero();
    }
  }

  @Test
  void payment_shouldNotReleaseTwice_whenCallbackArrivesAfterExpiry() {
    Order order = seed(Instant.now().minusSeconds(60));
    orders.getBean(OrderService.class).expirePendingPayments(Instant.now());
    PaymentEvent event = event(order, 100L);
    orders.getBean(OrderService.class).handlePaymentSucceeded(event);
    orders.getBean(OrderService.class).handlePaymentSucceeded(event);
    assertStock(order, "PAYMENT_REJECTED", 10, 0);
    assertThat(count(orders, "SELECT count(*) FROM payment_event_history WHERE order_id=?", order.getId()))
        .isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderCancelled'",
        order.getId())).isEqualTo(1);
    assertThat(count(orders, "SELECT count(*) FROM outbox_events WHERE aggregate_id=? AND event_type='OrderConfirmed'",
        order.getId())).isZero();
  }

  @Test
  void payment_shouldRollbackInventoryAndHistory_whenTransactionFails() {
    Order order = seed(Instant.now().plusSeconds(600));
    PaymentEvent event = event(order, 100L);
    assertThatThrownBy(() -> tx().executeWithoutResult(status -> {
      orders.getBean(OrderService.class).handlePaymentSucceeded(event);
      throw new IllegalStateException("simulate crash before commit");
    })).isInstanceOf(IllegalStateException.class);
    assertStock(order, "PENDING", 10, 2);
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
      return orders.getBean(OrderRepository.class).saveAndFlush(order);
    });
  }

  private PaymentEvent event(Order order, long amount) {
    return new PaymentEvent(UUID.randomUUID().toString(), "PaymentSucceeded",
        order.getId() + 100000, order.getId(), amount, "VND", order.getPaymentDueAt(), null);
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
