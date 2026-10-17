package com.ecommerce.identity.services;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.identity.models.dtos.responses.CheckoutContextResponse;
import com.ecommerce.identity.models.entities.Address;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.AddressRepository;
import com.ecommerce.identity.repositories.UserRepository;
import com.ecommerce.identity.services.impls.AddressServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AddressServiceImplTest {
  @Test
  void checkoutContextShouldRejectLockedUser() {
    AddressRepository addresses = mock(AddressRepository.class);
    UserRepository users = mock(UserRepository.class);
    User user = new User("user@example.com", "hash", "User", java.util.Set.of());
    user.lock();
    when(users.findById(7L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> new AddressServiceImpl(addresses, users).checkoutContext(7L, 1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACCOUNT_NOT_ACTIVE");
  }

  @Test
  void listShouldRejectLockedUser() {
    AddressRepository addresses = mock(AddressRepository.class);
    UserRepository users = mock(UserRepository.class);
    User user = new User("user@example.com", "hash", "User", java.util.Set.of());
    user.lock();
    when(users.findById(7L)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> new AddressServiceImpl(addresses, users).list(7L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ACCOUNT_NOT_ACTIVE");
  }

  @Test
  void checkoutContextShouldReturnOwnedAddressSnapshotForActiveUser() {
    AddressRepository addresses = mock(AddressRepository.class);
    UserRepository users = mock(UserRepository.class);
    User user = new User("user@example.com", "hash", "User", java.util.Set.of());
    org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 7L);
    when(users.findById(7L)).thenReturn(Optional.of(user));
    Address address =
        new Address(
            7L, "Nguyễn Văn A", "0900000000", "1 Đường A", "Phường 1", "Quận 1", "TP HCM", true);
    org.springframework.test.util.ReflectionTestUtils.setField(address, "id", 1L);
    when(addresses.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(address));

    CheckoutContextResponse context =
        new AddressServiceImpl(addresses, users).checkoutContext(7L, 1L);

    org.assertj.core.api.Assertions.assertThat(context.userId()).isEqualTo(7L);
    org.assertj.core.api.Assertions.assertThat(context.status()).isEqualTo(UserStatus.ACTIVE);
    org.assertj.core.api.Assertions.assertThat(context.address().province()).isEqualTo("TP HCM");
  }

  @Test
  void deletingDefaultAddressShouldNotPromoteAnotherAddress() {
    AddressRepository addresses = mock(AddressRepository.class);
    UserRepository users = mock(UserRepository.class);
    User user = new User("user@example.com", "hash", "User", java.util.Set.of());
    Address address =
        new Address(
            7L, "Nguyễn Văn A", "0900000000", "1 Đường A", "Phường 1", "Quận 1", "TP HCM", true);
    when(users.findById(7L)).thenReturn(Optional.of(user));
    when(addresses.findByIdAndUserId(1L, 7L)).thenReturn(Optional.of(address));

    new AddressServiceImpl(addresses, users).delete(7L, 1L);

    verify(addresses).delete(address);
    verify(addresses, never()).findByUserIdForUpdate(7L);
  }
}
