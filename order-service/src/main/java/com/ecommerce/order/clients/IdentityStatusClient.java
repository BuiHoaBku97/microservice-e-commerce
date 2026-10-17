package com.ecommerce.order.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(
    name = "identity-service",
    contextId = "identityStatusClient",
    configuration = IdentityStatusClientConfiguration.class)
public interface IdentityStatusClient {
  @GetMapping("/internal/v1/users/{userId}/status")
  String getStatus(@PathVariable Long userId);
}
