package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.CheckoutContextRequest;
import com.ecommerce.identity.models.dtos.responses.CheckoutContextResponse;
import com.ecommerce.identity.services.AddressService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/users/current")
public class InternalIdentityController {
  private final AddressService addressService;

  public InternalIdentityController(AddressService addressService) {
    this.addressService = addressService;
  }

  @PostMapping("/checkout-context")
  public CheckoutContextResponse checkoutContext(
      Authentication authentication, @Valid @RequestBody CheckoutContextRequest request) {
    return addressService.checkoutContext(Long.valueOf(authentication.getName()), request.addressId());
  }
}
