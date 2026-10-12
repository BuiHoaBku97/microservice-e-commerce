package com.ecommerce.order.clients;

import feign.Request;
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
}
