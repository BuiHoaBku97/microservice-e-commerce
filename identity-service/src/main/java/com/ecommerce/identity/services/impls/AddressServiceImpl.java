package com.ecommerce.identity.services.impls;

import com.ecommerce.identity.models.dtos.requests.AddressRequest;
import com.ecommerce.identity.models.dtos.responses.AddressResponse;
import com.ecommerce.identity.models.dtos.responses.CheckoutContextResponse;
import com.ecommerce.identity.models.entities.Address;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.AddressRepository;
import com.ecommerce.identity.repositories.UserRepository;
import com.ecommerce.identity.services.AddressService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AddressServiceImpl implements AddressService {
  private final AddressRepository addressRepository;
  private final UserRepository userRepository;

  public AddressServiceImpl(AddressRepository addressRepository, UserRepository userRepository) {
    this.addressRepository = addressRepository;
    this.userRepository = userRepository;
  }

  @Override
  @Transactional(readOnly = true)
  public List<AddressResponse> list(Long userId) {
    return addressRepository.findByUserIdOrderByDefaultAddressDescIdAsc(userId).stream()
        .map(AddressResponse::from)
        .toList();
  }

  @Override
  @Transactional
  public AddressResponse create(Long userId, AddressRequest request) {
    ensureActive(userId);
    List<Address> addresses = addressRepository.findByUserIdForUpdate(userId);
    Address address = new Address(userId, request.recipientName(), request.recipientPhone(),
        request.addressLine(), request.ward(), request.district(), request.province(),
        request.defaultAddress() || addresses.isEmpty());
    if (address.isDefaultAddress()) {
      addresses.forEach(item -> item.setDefaultAddress(false));
    }
    return AddressResponse.from(addressRepository.save(address));
  }

  @Override
  @Transactional
  public AddressResponse update(Long userId, Long addressId, AddressRequest request) {
    ensureActive(userId);
    Address address = getOwned(userId, addressId);
    if (request.defaultAddress()) {
      addressRepository.findByUserIdForUpdate(userId).forEach(item -> item.setDefaultAddress(false));
    }
    address.update(request.recipientName(), request.recipientPhone(), request.addressLine(),
        request.ward(), request.district(), request.province(), request.defaultAddress());
    return AddressResponse.from(addressRepository.save(address));
  }

  @Override
  @Transactional
  public void delete(Long userId, Long addressId) {
    ensureActive(userId);
    Address address = getOwned(userId, addressId);
    addressRepository.delete(address);
    if (address.isDefaultAddress()) {
      addressRepository.findByUserIdForUpdate(userId).stream().findFirst()
          .ifPresent(item -> item.setDefaultAddress(true));
    }
  }

  @Override
  @Transactional
  public AddressResponse makeDefault(Long userId, Long addressId) {
    ensureActive(userId);
    Address address = getOwned(userId, addressId);
    addressRepository.findByUserIdForUpdate(userId).forEach(item -> item.setDefaultAddress(false));
    address.setDefaultAddress(true);
    return AddressResponse.from(addressRepository.save(address));
  }

  @Override
  @Transactional(readOnly = true)
  public CheckoutContextResponse checkoutContext(Long userId, Long addressId) {
    User user = ensureActive(userId);
    Address address = getOwned(userId, addressId);
    return new CheckoutContextResponse(
        user.getId(),
        user.getStatus(),
        new CheckoutContextResponse.AddressSnapshot(
            address.getId(),
            address.getRecipientName(),
            address.getRecipientPhone(),
            address.getAddressLine(),
            address.getWard(),
            address.getDistrict(),
            address.getProvince()));
  }

  private User ensureActive(Long userId) {
    User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new IllegalStateException("ACCOUNT_NOT_ACTIVE");
    }
    return user;
  }

  private Address getOwned(Long userId, Long addressId) {
    return addressRepository.findByIdAndUserId(addressId, userId)
        .orElseThrow(() -> new IllegalArgumentException("ADDRESS_NOT_FOUND"));
  }
}
