package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.ChangeUserStatusRequest;
import com.ecommerce.identity.models.dtos.responses.ApiResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.models.dtos.responses.PageResponse;
import com.ecommerce.identity.models.entities.UserStatus;
import com.ecommerce.identity.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
  private final UserService users;

  public AdminUserController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public ApiResponse<PageResponse<UserResponse>> list(
      @RequestParam(required = false) UserStatus status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest request) {
    return ApiResponse.success(PageResponse.from(users.list(status, keyword,
        PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)))), request.getRequestId());
  }

  @GetMapping("/{id}")
  public ApiResponse<UserResponse> get(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(users.get(id), request.getRequestId());
  }

  @PostMapping("/{id}/lock")
  public ApiResponse<UserResponse> lock(
      @PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(users.lock(id, body), request.getRequestId());
  }

  @PostMapping("/{id}/unlock")
  public ApiResponse<UserResponse> unlock(
      @PathVariable Long id, @Valid @RequestBody ChangeUserStatusRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(users.unlock(id, body), request.getRequestId());
  }
}
