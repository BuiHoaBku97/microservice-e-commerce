package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.CheckoutContextRequest;
import com.ecommerce.identity.models.dtos.responses.CheckoutContextResponse;
import com.ecommerce.identity.services.AddressService;
import com.ecommerce.identity.services.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users")
public class InternalIdentityController {
  private final AddressService addressService;
  private final UserService userService;

  public InternalIdentityController(AddressService addressService, UserService userService) {
    this.addressService = addressService;
    this.userService = userService;
  }

  @PostMapping("/current/checkout-context")
  public CheckoutContextResponse checkoutContext(
      Authentication authentication, @Valid @RequestBody CheckoutContextRequest request) {
    return addressService.checkoutContext(
        Long.valueOf(authentication.getName()), request.addressId());
  }

  @GetMapping(value = "/{userId}/status", produces = "text/plain")
  public String status(@PathVariable Long userId) {
    return userService.currentStatus(userId).name();
  }
}
