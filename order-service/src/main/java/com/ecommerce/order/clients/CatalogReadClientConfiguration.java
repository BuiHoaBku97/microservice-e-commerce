package com.ecommerce.order.clients;

import com.ecommerce.order.configs.CorrelationContext;
import com.ecommerce.order.configs.ServiceJwtProvider;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class CatalogReadClientConfiguration {
  @Bean
  Request.Options catalogReadRequestOptions(
      @Value("${app.clients.catalog.connect-timeout-ms:2000}") long connectTimeout,
      @Value("${app.clients.catalog.read-timeout-ms:3000}") long readTimeout) {
    return new Request.Options(
        connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, false);
  }

  @Bean
  Retryer catalogReadRetryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  RequestInterceptor catalogReadServiceTokenInterceptor(ServiceJwtProvider serviceJwtProvider) {
    return template -> {
      template.header(
          "X-Service-Authorization",
          "Bearer " + serviceJwtProvider.issue("catalog-service", "catalog.read"));
      template.header(CorrelationContext.HEADER, CorrelationContext.currentOrCreate());
    };
  }
}
