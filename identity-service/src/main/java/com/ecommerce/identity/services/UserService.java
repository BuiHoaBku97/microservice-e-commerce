package com.ecommerce.identity.services;

import com.ecommerce.identity.exceptions.AccountNotActiveException;
import com.ecommerce.identity.models.dtos.requests.ChangeUserStatusRequest;
import com.ecommerce.identity.models.dtos.requests.UpdateProfileRequest;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.models.entities.User;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.repositories.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final UserRepository users;

  public UserService(UserRepository users) {
    this.users = users;
  }

  @Transactional(readOnly = true)
  public UserResponse me(Long userId) {
    return toResponse(activeUser(userId));
  }

  @Transactional
  public UserResponse updateMe(Long userId, UpdateProfileRequest request) {
    User user = activeUser(userId);
    requireVersion(user, request.version());
    user.updateProfile(request.fullName().trim(), normalizePhone(request.phone()));
    return toResponse(users.save(user));
  }

  @Transactional(readOnly = true)
  public Page<UserResponse> list(UserStatus status, String keyword, Pageable pageable) {
    String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
    return users.findForAdmin(status, normalized, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public UserResponse get(Long id) {
    return toResponse(find(id));
  }

  @Transactional
  public UserResponse lock(Long id, ChangeUserStatusRequest request) {
    User user = find(id);
    if (user.getStatus() == UserStatus.LOCKED) return toResponse(user);
    requireVersion(user, request.version());
    user.lock();
    return toResponse(users.save(user));
  }

  @Transactional
  public UserResponse unlock(Long id, ChangeUserStatusRequest request) {
    User user = find(id);
    if (user.getStatus() == UserStatus.ACTIVE) return toResponse(user);
    requireVersion(user, request.version());
    user.unlock();
    return toResponse(users.save(user));
  }

  private User activeUser(Long id) {
    User user = find(id);
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new AccountNotActiveException("Tài khoản không ở trạng thái hoạt động");
    }
    return user;
  }

  private User find(Long id) {
    return users.findById(id).orElseThrow(() -> new IllegalArgumentException("USER_NOT_FOUND"));
  }

  private void requireVersion(User user, Long version) {
    if (!java.util.Objects.equals(user.getVersion(), version)) {
      throw new IllegalStateException("VERSION_CONFLICT");
    }
  }

  private String normalizePhone(String phone) {
    return phone == null || phone.isBlank() ? null : phone.trim();
  }

  private UserResponse toResponse(User user) {
    return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getPhone(),
        user.getStatus(), user.getRoles(), user.getVersion());
  }
}
