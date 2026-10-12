package com.ecommerce.identity.models.dtos.responses;

import com.ecommerce.identity.models.entities.UserStatus;

public record CheckoutContextResponse(
    Long userId, UserStatus status, AddressSnapshot address) {
  public record AddressSnapshot(
      Long id,
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province) {}
}
