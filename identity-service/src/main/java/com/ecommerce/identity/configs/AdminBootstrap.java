package com.ecommerce.identity.configs;

import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.repositories.UserRepository;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(name = "app.admin-bootstrap.enabled", havingValue = "true")
public class AdminBootstrap implements ApplicationRunner {
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final String email;
  private final String password;
  private final String fullName;

  public AdminBootstrap(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.admin-bootstrap.email:}") String email,
      @Value("${app.admin-bootstrap.password:}") String password,
      @Value("${app.admin-bootstrap.full-name:System Administrator}") String fullName) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.email = email;
    this.password = password;
    this.fullName = fullName;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments arguments) {
    if (email.isBlank() || password.length() < 8) {
      throw new IllegalStateException(
          "ADMIN_EMAIL and ADMIN_PASSWORD with at least 8 characters are required");
    }
    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
    User administrator =
        userRepository
            .findByEmail(normalizedEmail)
            .orElseGet(
                () ->
                    new User(
                        normalizedEmail,
                        passwordEncoder.encode(password),
                        fullName.trim(),
                        Set.of(RoleName.ADMIN)));
    administrator.grantRole(RoleName.ADMIN);
    userRepository.save(administrator);
  }
}
