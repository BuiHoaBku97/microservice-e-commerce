package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.UpdateProfileRequest;
import com.ecommerce.identity.models.dtos.responses.ApiResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.services.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {
  private final UserService users;

  public UserController(UserService users) {
    this.users = users;
  }

  @GetMapping
  public ApiResponse<UserResponse> me(Authentication authentication, HttpServletRequest request) {
    return ApiResponse.success(users.me(Long.valueOf(authentication.getName())), request.getRequestId());
  }

  @PatchMapping
  public ApiResponse<UserResponse> update(
      Authentication authentication, @Valid @RequestBody UpdateProfileRequest body,
      HttpServletRequest request) {
    return ApiResponse.success(
        users.updateMe(Long.valueOf(authentication.getName()), body), request.getRequestId());
  }
}
