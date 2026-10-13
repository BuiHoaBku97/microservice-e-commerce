package com.ecommerce.identity.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.identity.exceptions.AccountNotActiveException;
import com.ecommerce.identity.exceptions.PasswordIncorrectException;
import com.ecommerce.identity.models.dtos.requests.LoginRequest;
import com.ecommerce.identity.models.dtos.requests.RegisterRequest;
import com.ecommerce.identity.models.dtos.requests.RefreshTokenRequest;
import com.ecommerce.identity.models.dtos.responses.TokenResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.RefreshToken;
import com.ecommerce.identity.repositories.UserRepository;
import com.ecommerce.identity.repositories.RefreshTokenRepository;
import com.ecommerce.identity.services.impls.IdentityServiceImpl;
import com.ecommerce.identity.utils.JwtTokenService;
import java.util.Set;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class IdentityServiceImplTest {

  @Mock private UserRepository userRepository;

  @Mock private PasswordEncoder passwordEncoder;

  @Mock private JwtTokenService jwtTokenService;

  @Mock private RefreshTokenRepository refreshTokenRepository;

  private IdentityServiceImpl identityService;

  @BeforeEach
  void setUp() {
    identityService = new IdentityServiceImpl(
        userRepository, passwordEncoder, jwtTokenService, refreshTokenRepository, 2_592_000L);
  }

  @Test
  void register_shouldCreateCustomerWithNormalizedEmail() {
    RegisterRequest request =
        new RegisterRequest(" USER@Example.COM ", "password123", " Nguyễn Văn A ");
    when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
    when(passwordEncoder.encode("password123")).thenReturn("encoded");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserResponse response = identityService.register(request);

    ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getEmail()).isEqualTo("user@example.com");
    assertThat(captor.getValue().getFullName()).isEqualTo("Nguyễn Văn A");
    assertThat(captor.getValue().getRoles()).containsExactly(RoleName.CUSTOMER);
    assertThat(response.email()).isEqualTo("user@example.com");
  }

  @Test
  void register_shouldRejectExistingEmail() {
    when(userRepository.existsByEmail("user@example.com")).thenReturn(true);

    assertThatThrownBy(
            () ->
                identityService.register(
                    new RegisterRequest("user@example.com", "password123", "Người dùng")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("EMAIL_EXISTS");

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  void login_shouldReportIncorrectPassword() {
    User user = new User("user@example.com", "encoded", "Người dùng", Set.of(RoleName.CUSTOMER));
    when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));
    when(passwordEncoder.matches("wrong", "encoded")).thenReturn(false);

    assertThatThrownBy(() -> identityService.login(new LoginRequest("user@example.com", "wrong")))
        .isInstanceOf(PasswordIncorrectException.class)
        .hasMessage("Mật khẩu không đúng");
  }

  @Test
  void login_shouldReportInactiveAccount() {
    User user = new User("user@example.com", "encoded", "Người dùng", Set.of(RoleName.CUSTOMER));
    user.lock();
    when(userRepository.findByEmail("user@example.com")).thenReturn(java.util.Optional.of(user));

    assertThatThrownBy(
            () -> identityService.login(new LoginRequest("user@example.com", "password")))
        .isInstanceOf(AccountNotActiveException.class)
        .hasMessage("Tài khoản không ở trạng thái hoạt động");
    verify(passwordEncoder, never()).matches(any(), any());
  }

  @Test
  void refresh_shouldRotateTokenAndRevokePreviousToken() throws Exception {
    User user = new User("user@example.com", "encoded", "Người dùng", Set.of(RoleName.CUSTOMER));
    String rawToken = "old-refresh-token";
    RefreshToken current = new RefreshToken(
        user, sha256(rawToken), Instant.now().plusSeconds(300));
    when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
        .thenReturn(Optional.of(current));
    when(jwtTokenService.issueAccessToken(user)).thenReturn("new-access-token");
    when(jwtTokenService.accessTokenSeconds()).thenReturn(900L);

    TokenResponse response = identityService.refresh(new RefreshTokenRequest(rawToken));

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isNotBlank().isNotEqualTo(rawToken);
    assertThat(current.getRevokedAt()).isNotNull();
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());
    assertThat(current.getReplacedByHash()).isEqualTo(captor.getValue().getTokenHash());
  }

  @Test
  void refresh_shouldRejectReplayedRotatedToken() throws Exception {
    User user = new User("user@example.com", "encoded", "Người dùng", Set.of(RoleName.CUSTOMER));
    String rawToken = "old-refresh-token";
    RefreshToken current = new RefreshToken(
        user, sha256(rawToken), Instant.now().plusSeconds(300));
    current.revoke("replacement-hash");
    when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
        .thenReturn(Optional.of(current));

    assertThatThrownBy(() -> identityService.refresh(new RefreshTokenRequest(rawToken)))
        .isInstanceOf(IllegalArgumentException.class);
    verify(jwtTokenService, never()).issueAccessToken(any());
  }

  @Test
  void logout_shouldBeIdempotentForAlreadyRevokedSession() throws Exception {
    String rawToken = "refresh-token";
    User user = new User("user@example.com", "encoded", "Người dùng", Set.of(RoleName.CUSTOMER));
    RefreshToken token = new RefreshToken(user, sha256(rawToken), Instant.now().plusSeconds(300));
    token.revoke(null);
    Instant revokedAt = token.getRevokedAt();
    when(refreshTokenRepository.findByTokenHashForUpdate(sha256(rawToken)))
        .thenReturn(Optional.of(token));

    identityService.logout(new RefreshTokenRequest(rawToken));

    assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
  }

  private String sha256(String value) throws Exception {
    return HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
  }
}
