package com.ecommerce.identity.services;

import com.ecommerce.identity.models.dtos.requests.AddressRequest;
import com.ecommerce.identity.models.dtos.responses.AddressResponse;
import com.ecommerce.identity.models.dtos.responses.CheckoutContextResponse;
import java.util.List;

public interface AddressService {
  List<AddressResponse> list(Long userId);

  AddressResponse create(Long userId, AddressRequest request);

  AddressResponse update(Long userId, Long addressId, AddressRequest request);

  void delete(Long userId, Long addressId);

  AddressResponse makeDefault(Long userId, Long addressId);

  CheckoutContextResponse checkoutContext(Long userId, Long addressId);
}
