package com.ecommerce.order.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "identity-service", configuration = IdentityClientConfiguration.class)
public interface IdentityClient {
  @PostMapping("/internal/v1/users/current/checkout-context")
  IdentityCheckoutContext getCheckoutContext(
      @RequestBody IdentityCheckoutContextRequest request,
      @RequestHeader("Authorization") String authorization);
}
