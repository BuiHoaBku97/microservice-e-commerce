package com.ecommerce.order.clients;

import com.ecommerce.order.configs.ServiceJwtProvider;
import com.ecommerce.order.configs.CorrelationContext;
import feign.Request;
import feign.RequestInterceptor;
import feign.Retryer;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class IdentityStatusClientConfiguration {
  @Bean
  Request.Options identityStatusRequestOptions(
      @Value("${app.clients.identity.connect-timeout-ms:2000}") long connectTimeout,
      @Value("${app.clients.identity.read-timeout-ms:3000}") long readTimeout) {
    return new Request.Options(
        connectTimeout, TimeUnit.MILLISECONDS, readTimeout, TimeUnit.MILLISECONDS, false);
  }

  @Bean
  Retryer identityStatusRetryer() {
    return Retryer.NEVER_RETRY;
  }

  @Bean
  RequestInterceptor identityStatusServiceTokenInterceptor(ServiceJwtProvider serviceJwtProvider) {
    return template -> {
        template.header(
            "X-Service-Authorization",
            "Bearer " + serviceJwtProvider.issue("identity-service", "identity.status"));
        template.header(CorrelationContext.HEADER, CorrelationContext.currentOrCreate());
    };
  }
}
