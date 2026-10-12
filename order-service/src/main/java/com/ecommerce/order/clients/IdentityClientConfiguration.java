package com.ecommerce.order.clients;

import feign.Request;
import feign.Retryer;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Value;
import feign.RequestInterceptor;

public class IdentityClientConfiguration {
  @Bean
  Request.Options identityRequestOptions() {
    return new Request.Options(2000, 3000);
  }

  @Bean
  Retryer identityRetryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  RequestInterceptor identityServiceTokenInterceptor(
      @Value("${app.clients.internal-service-token:}") String serviceToken) {
    return template -> template.header("X-Internal-Service-Token", serviceToken);
  }
}
