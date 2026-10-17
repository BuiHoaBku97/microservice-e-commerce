package com.ecommerce.integrations;

import static org.mockito.Mockito.mock;

import com.ecommerce.order.clients.CatalogClient;
import com.ecommerce.order.clients.IdentityClient;
import com.ecommerce.order.clients.IdentityStatusClient;
import com.ecommerce.order.services.CartService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.transaction.annotation.EnableTransactionManagement;

final class ServiceContexts {
  @Configuration(proxyBeanMethods = false)
  @EnableKafka
  @EnableTransactionManagement
  @ImportAutoConfiguration({DataSourceAutoConfiguration.class, JdbcTemplateAutoConfiguration.class,
      HibernateJpaAutoConfiguration.class, TransactionAutoConfiguration.class,
      FlywayAutoConfiguration.class, JacksonAutoConfiguration.class, KafkaAutoConfiguration.class})
  static class Infrastructure {
    @Bean MeterRegistry meterRegistry() { return new SimpleMeterRegistry(); }
  }

  @Configuration(proxyBeanMethods = false)
  @Import({Infrastructure.class, com.ecommerce.order.services.OrderService.class,
      com.ecommerce.order.services.DatabaseClock.class,
      com.ecommerce.order.services.InventoryService.class,
      com.ecommerce.order.services.OutboxEventService.class,
      com.ecommerce.order.messaging.publishers.OutboxPublisher.class,
      com.ecommerce.order.messaging.consumers.PaymentEventConsumer.class,
      com.ecommerce.order.configs.KafkaConsumerConfig.class})
  @EntityScan("com.ecommerce.order.models.entities")
  @EnableJpaRepositories("com.ecommerce.order.repositories")
  static class Orders {
    // Checkout HTTP dependencies are outside the payment event flow under test.
    @Bean CartService cartService() { return mock(CartService.class); }
    @Bean CatalogClient catalogClient() { return mock(CatalogClient.class); }
    @Bean IdentityClient identityClient() { return mock(IdentityClient.class); }
    @Bean IdentityStatusClient identityStatusClient() { return mock(IdentityStatusClient.class); }
    @Bean(destroyMethod = "shutdown")
    RedissonClient redissonClient(@Value("${test.redis.address}") String address) {
      Config config = new Config();
      config.useSingleServer().setAddress(address);
      return Redisson.create(config);
    }
  }

  @Configuration(proxyBeanMethods = false)
  @Import({Infrastructure.class, com.ecommerce.payment.services.PaymentService.class,
      com.ecommerce.payment.services.PaymentEventService.class,
      com.ecommerce.payment.messaging.publishers.OutboxPublisher.class,
      com.ecommerce.payment.messaging.consumers.OrderEventConsumer.class,
      com.ecommerce.payment.configs.KafkaConsumerConfig.class})
  @EntityScan("com.ecommerce.payment.models.entities")
  @EnableJpaRepositories("com.ecommerce.payment.repositories")
  static class Payments {}

  @Configuration(proxyBeanMethods = false)
  @Import({Infrastructure.class, com.ecommerce.notification.services.NotificationService.class,
      com.ecommerce.notification.messaging.consumers.OrderEventConsumer.class,
      com.ecommerce.notification.messaging.consumers.PaymentEventConsumer.class,
      com.ecommerce.notification.configs.KafkaConsumerConfig.class})
  @EntityScan("com.ecommerce.notification.models.entities")
  @EnableJpaRepositories("com.ecommerce.notification.repositories")
  static class Notifications {}
}
