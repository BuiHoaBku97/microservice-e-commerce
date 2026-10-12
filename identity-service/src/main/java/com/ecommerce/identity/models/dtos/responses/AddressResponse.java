package com.ecommerce.identity.models.dtos.responses;

import com.ecommerce.identity.models.entities.Address;

public record AddressResponse(
    Long id,
    Long userId,
    String recipientName,
    String recipientPhone,
    String addressLine,
    String ward,
    String district,
    String province,
    boolean defaultAddress,
    Long version) {
  public static AddressResponse from(Address address) {
    return new AddressResponse(
        address.getId(),
        address.getUserId(),
        address.getRecipientName(),
        address.getRecipientPhone(),
        address.getAddressLine(),
        address.getWard(),
        address.getDistrict(),
        address.getProvince(),
        address.isDefaultAddress(),
        address.getVersion());
  }
}
