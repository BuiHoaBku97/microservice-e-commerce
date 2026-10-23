package com.ecommerce.identity.services.impls;

import com.ecommerce.identity.exceptions.AccountNotActiveException;
import com.ecommerce.identity.exceptions.PasswordIncorrectException;
import com.ecommerce.identity.models.dtos.requests.LoginRequest;
import com.ecommerce.identity.models.dtos.requests.RefreshTokenRequest;
import com.ecommerce.identity.models.dtos.requests.RegisterRequest;
import com.ecommerce.identity.models.dtos.responses.TokenResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.models.entities.RefreshToken;
import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.RefreshTokenRepository;
import com.ecommerce.identity.repositories.UserRepository;
import com.ecommerce.identity.services.IdentityService;
import com.ecommerce.identity.utils.JwtTokenService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdentityServiceImpl implements IdentityService {

  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenRepository refreshTokenRepository;
  private final long refreshTokenSeconds;
  private final SecureRandom secureRandom = new SecureRandom();

  public IdentityServiceImpl(
      UserRepository userRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      RefreshTokenRepository refreshTokenRepository,
      @Value("${security.jwt.refresh-token-seconds:2592000}") long refreshTokenSeconds) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.refreshTokenRepository = refreshTokenRepository;
    this.refreshTokenSeconds = refreshTokenSeconds;
  }

  @Override
  @Transactional
  public UserResponse register(RegisterRequest request) {
    String email = normalizeEmail(request.email());
    if (userRepository.existsByEmail(email)) {
      throw new IllegalStateException("EMAIL_EXISTS");
    }
    try {
      User user =
          new User(
              email,
              passwordEncoder.encode(request.password()),
              request.fullName().trim(),
              Set.of(RoleName.CUSTOMER));
      return toResponse(userRepository.save(user));
    } catch (DataIntegrityViolationException exception) {
      throw new IllegalStateException("EMAIL_EXISTS", exception);
    }
  }

  @Override
  @Transactional
  public TokenResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByEmail(normalizeEmail(request.email()))
            .orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new AccountNotActiveException("ACCOUNT_NOT_ACTIVE");
    }
    if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
      throw new PasswordIncorrectException("PASSWORD_INCORRECT");
    }
    return issueSession(user);
  }

  @Override
  @Transactional
  public TokenResponse refresh(RefreshTokenRequest request) {
    String currentRefreshTokenHash = hash(request.refreshToken());
    RefreshToken currentRefreshToken =
        refreshTokenRepository
            .findByTokenHashForUpdate(currentRefreshTokenHash)
            .orElseThrow(
                () -> new IllegalArgumentException("REFRESH_TOKEN_INVALID"));
    Instant now = Instant.now();
    if (!currentRefreshToken.isUsableAt(now)) {
      throw new IllegalArgumentException("REFRESH_TOKEN_INVALID");
    }
    if (currentRefreshToken.getUser().getStatus() != UserStatus.ACTIVE) {
      throw new AccountNotActiveException("ACCOUNT_NOT_ACTIVE");
    }
    String rawRefreshToken = newRefreshToken();
    String replacementRefreshTokenHash = hash(rawRefreshToken);
    RefreshToken replacementRefreshToken =
        new RefreshToken(
            currentRefreshToken.getUser(),
            replacementRefreshTokenHash,
            now.plusSeconds(refreshTokenSeconds),
            currentRefreshToken.getSessionId());
    RefreshToken savedReplacementRefreshToken =
        refreshTokenRepository.save(replacementRefreshToken);
    currentRefreshToken.replaceWith(
        savedReplacementRefreshToken == null
            ? replacementRefreshToken
            : savedReplacementRefreshToken);
    return response(currentRefreshToken.getUser(), rawRefreshToken);
  }

  @Override
  @Transactional
  public void logout(RefreshTokenRequest request) {
    RefreshToken refreshToken =
        refreshTokenRepository.findByTokenHashForUpdate(hash(request.refreshToken())).orElse(null);
    if (refreshToken == null) {
      return;
    }
    refreshTokenRepository
        .findActiveBySessionIdForUpdate(refreshToken.getSessionId())
        .forEach(activeRefreshToken -> activeRefreshToken.revoke(null));
  }

  private TokenResponse issueSession(User user) {
    String rawRefreshToken = newRefreshToken();
    refreshTokenRepository.save(
        new RefreshToken(
            user, hash(rawRefreshToken), Instant.now().plusSeconds(refreshTokenSeconds)));
    return response(user, rawRefreshToken);
  }

  private TokenResponse response(User user, String refreshToken) {
    return new TokenResponse(
        jwtTokenService.issueAccessToken(user),
        refreshToken,
        "Bearer",
        jwtTokenService.accessTokenSeconds());
  }

  private String newRefreshToken() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String hash(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("REFRESH_TOKEN_HASH_FAILED", exception);
    }
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase();
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getEmail(),
        user.getFullName(),
        user.getPhone(),
        user.getStatus(),
        user.getRoles(),
        user.getVersion());
  }
}
