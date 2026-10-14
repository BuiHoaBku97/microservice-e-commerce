package com.ecommerce.identity.configs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.repositories.UserRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

class AdminBootstrapTest {
  @Test
  void createsAdministratorFromNormalizedConfiguration() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.empty());
    when(passwordEncoder.encode("secure-password")).thenReturn("encoded-password");
    AdminBootstrap bootstrap =
        new AdminBootstrap(
            userRepository,
            passwordEncoder,
            "  ADMIN@EXAMPLE.COM ",
            "secure-password",
            "System Administrator");

    bootstrap.run(new DefaultApplicationArguments());

    ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(userCaptor.capture());
    User administrator = userCaptor.getValue();
    assertThat(administrator.getEmail()).isEqualTo("admin@example.com");
    assertThat(administrator.getPasswordHash()).isEqualTo("encoded-password");
    assertThat(administrator.getRoles()).isEqualTo(Set.of(RoleName.ADMIN));
  }

  @Test
  void grantsAdministratorRoleToExistingCustomer() throws Exception {
    UserRepository userRepository = mock(UserRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    User customer =
        new User(
            "admin@example.com", "existing-password", "Administrator", Set.of(RoleName.CUSTOMER));
    when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(customer));
    AdminBootstrap bootstrap =
        new AdminBootstrap(
            userRepository,
            passwordEncoder,
            "admin@example.com",
            "secure-password",
            "System Administrator");

    bootstrap.run(new DefaultApplicationArguments());

    verify(userRepository).save(customer);
    assertThat(customer.getRoles()).containsExactlyInAnyOrder(RoleName.CUSTOMER, RoleName.ADMIN);
    assertThat(customer.getPasswordHash()).isEqualTo("existing-password");
  }
}
