package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.AddressRequest;
import com.ecommerce.identity.models.dtos.responses.AddressResponse;
import com.ecommerce.identity.services.AddressService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me/addresses")
public class AddressController {
  private final AddressService addressService;

  public AddressController(AddressService addressService) {
    this.addressService = addressService;
  }

  @GetMapping
  public List<AddressResponse> list(Authentication authentication) {
    return addressService.list(userId(authentication));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AddressResponse create(
      Authentication authentication, @Valid @RequestBody AddressRequest request) {
    return addressService.create(userId(authentication), request);
  }

  @PatchMapping("/{addressId}")
  public AddressResponse update(
      Authentication authentication,
      @PathVariable Long addressId,
      @Valid @RequestBody AddressRequest request) {
    return addressService.update(userId(authentication), addressId, request);
  }

  @DeleteMapping("/{addressId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(Authentication authentication, @PathVariable Long addressId) {
    addressService.delete(userId(authentication), addressId);
  }

  @PutMapping("/{addressId}/default")
  public AddressResponse makeDefault(Authentication authentication, @PathVariable Long addressId) {
    return addressService.makeDefault(userId(authentication), addressId);
  }

  private Long userId(Authentication authentication) {
    return Long.valueOf(authentication.getName());
  }
}
