package com.ecommerce.identity.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.identity.models.dtos.requests.ChangeUserStatusRequest;
import com.ecommerce.identity.models.entities.AccountStatusHistory;
import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.AccountStatusHistoryRepository;
import com.ecommerce.identity.repositories.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserServiceTest {
  @Test
  void refusesToLockTheLastActiveAdministrator() {
    UserRepository users = mock(UserRepository.class);
    AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
    User administrator = user(7L, RoleName.ADMIN);
    when(users.findById(7L)).thenReturn(Optional.of(administrator));
    when(users.countByRoleAndStatus(RoleName.ADMIN, UserStatus.ACTIVE)).thenReturn(1L);

    assertThatThrownBy(
            () -> new UserService(users, history).lock(7L, new ChangeUserStatusRequest("test", 0L)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("LAST_ACTIVE_ADMIN");

    verify(users).acquireActiveAdminLock();
    verify(users, never()).save(administrator);
  }

  @Test
  void locksAdministratorWhenAnotherActiveAdministratorRemains() {
    UserRepository users = mock(UserRepository.class);
    AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
    User administrator = user(7L, RoleName.ADMIN);
    when(users.findById(7L)).thenReturn(Optional.of(administrator));
    when(users.countByRoleAndStatus(RoleName.ADMIN, UserStatus.ACTIVE)).thenReturn(2L);
    when(users.save(administrator)).thenReturn(administrator);

    var response =
        new UserService(users, history)
            .lock(7L, new ChangeUserStatusRequest("security review", 0L));

    assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
    verify(users).acquireActiveAdminLock();
    verify(history).save(org.mockito.ArgumentMatchers.any(AccountStatusHistory.class));
  }

  @Test
  void lockingCustomerDoesNotDependOnAdministratorCount() {
    UserRepository users = mock(UserRepository.class);
    AccountStatusHistoryRepository history = mock(AccountStatusHistoryRepository.class);
    User customer = user(8L, RoleName.CUSTOMER);
    when(users.findById(8L)).thenReturn(Optional.of(customer));
    when(users.save(customer)).thenReturn(customer);

    var response =
        new UserService(users, history)
            .lock(8L, new ChangeUserStatusRequest("abuse", 0L));

    assertThat(response.status()).isEqualTo(UserStatus.LOCKED);
    verify(users, never()).countByRoleAndStatus(RoleName.ADMIN, UserStatus.ACTIVE);
  }

  private User user(Long id, RoleName role) {
    User user = new User(role.name().toLowerCase() + "@example.com", "hash", role.name(), Set.of(role));
    ReflectionTestUtils.setField(user, "id", id);
    ReflectionTestUtils.setField(user, "version", 0L);
    return user;
  }
}
