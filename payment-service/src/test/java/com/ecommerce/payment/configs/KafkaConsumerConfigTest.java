package com.ecommerce.payment.configs;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;

class KafkaConsumerConfigTest {
  @Test
  void shouldApplyManualAckAndStringCodecsFromApplicationConfiguration() {
    new ApplicationContextRunner()
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withConfiguration(AutoConfigurations.of(KafkaAutoConfiguration.class))
        .withUserConfiguration(KafkaConsumerConfig.class)
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var factory = context.getBean(ConcurrentKafkaListenerContainerFactory.class);
              var container = factory.createContainer("configuration-test");
              assertThat(container.getContainerProperties().getAckMode())
                  .isEqualTo(ContainerProperties.AckMode.MANUAL);
              var consumerProperties =
                  context.getBean(ConsumerFactory.class).getConfigurationProperties();
              assertThat(consumerProperties.get(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG))
                  .isEqualTo(false);
              assertThat(consumerProperties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG))
                  .isEqualTo(StringDeserializer.class);
              assertThat(consumerProperties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG))
                  .isEqualTo(StringDeserializer.class);
              var producerProperties =
                  context.getBean(ProducerFactory.class).getConfigurationProperties();
              assertThat(producerProperties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG))
                  .isEqualTo(StringSerializer.class);
              assertThat(producerProperties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG))
                  .isEqualTo(StringSerializer.class);
            });
  }
}

