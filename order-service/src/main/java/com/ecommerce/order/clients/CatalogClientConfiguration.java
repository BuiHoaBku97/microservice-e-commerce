package com.ecommerce.order.clients;

import feign.Request;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import feign.RequestInterceptor;

public class CatalogClientConfiguration {
  @Bean
  Request.Options catalogRequestOptions(
      @Value("${app.clients.catalog.connect-timeout-ms:2000}") long connectTimeout,
      @Value("${app.clients.catalog.read-timeout-ms:3000}") long readTimeout) {
    return new Request.Options(
        connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, false);
  }

  @Bean
  Retryer catalogRetryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  RequestInterceptor catalogServiceTokenInterceptor(
      @Value("${app.clients.internal-service-token:}") String serviceToken) {
    return template -> template.header("X-Internal-Service-Token", serviceToken);
  }
}
