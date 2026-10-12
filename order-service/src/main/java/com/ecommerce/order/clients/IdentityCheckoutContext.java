package com.ecommerce.order.clients;

public record IdentityCheckoutContext(Long userId, String status, AddressSnapshot address) {
  public record AddressSnapshot(
      Long id,
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province) {}
}
