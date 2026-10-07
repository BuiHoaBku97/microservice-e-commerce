package com.ecommerce.identity.services.impls;

import com.ecommerce.identity.exceptions.AccountNotActiveException;
import com.ecommerce.identity.exceptions.PasswordIncorrectException;
import com.ecommerce.identity.models.dtos.requests.LoginRequest;
import com.ecommerce.identity.models.dtos.requests.RefreshTokenRequest;
import com.ecommerce.identity.models.dtos.requests.RegisterRequest;
import com.ecommerce.identity.models.dtos.responses.TokenResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.UserRepository;
import com.ecommerce.identity.services.IdentityService;
import com.ecommerce.identity.utils.JwtTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class IdentityServiceImpl implements IdentityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) throw new IllegalStateException("EMAIL_EXISTS");
        try {
            User user = new User(email, passwordEncoder.encode(request.password()),
                    request.fullName().trim(), Set.of(RoleName.CUSTOMER));
            return toResponse(userRepository.save(user));
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("EMAIL_EXISTS", exception);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(() -> new PasswordIncorrectException("Mật khẩu không đúng"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException("Tài khoản không ở trạng thái hoạt động");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new PasswordIncorrectException("Mật khẩu không đúng");
        }
        return jwtTokenService.issue(user);
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refresh(RefreshTokenRequest request) {
        return jwtTokenService.refresh(request.refreshToken());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(),
                user.getPhone(), user.getStatus(), user.getRoles(), user.getVersion());
    }
}
