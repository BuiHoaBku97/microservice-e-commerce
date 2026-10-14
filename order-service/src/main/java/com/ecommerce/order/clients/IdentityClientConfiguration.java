package com.ecommerce.order.clients;

import com.ecommerce.order.configs.ServiceJwtProvider;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import org.springframework.context.annotation.Bean;

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
  RequestInterceptor identityServiceTokenInterceptor(ServiceJwtProvider serviceJwtProvider) {
    return template ->
        template.header(
            "X-Service-Authorization",
            "Bearer " + serviceJwtProvider.issue("identity-service", "identity.checkout"));
  }
}
