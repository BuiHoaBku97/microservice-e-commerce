package com.ecommerce.identity.controllers;

import com.ecommerce.identity.models.dtos.requests.LoginRequest;
import com.ecommerce.identity.models.dtos.requests.RefreshTokenRequest;
import com.ecommerce.identity.models.dtos.requests.RegisterRequest;
import com.ecommerce.identity.models.dtos.responses.ApiResponse;
import com.ecommerce.identity.models.dtos.responses.TokenResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;
import com.ecommerce.identity.services.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

  private final IdentityService identityService;

  public AuthController(IdentityService identityService) {
    this.identityService = identityService;
  }

  @PostMapping("/register")
  ResponseEntity<ApiResponse<UserResponse>> register(
      @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(ApiResponse.success(identityService.register(request), httpRequest.getRequestId()));
  }

  @PostMapping("/login")
  ResponseEntity<ApiResponse<TokenResponse>> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(identityService.login(request), httpRequest.getRequestId()));
  }

  @PostMapping("/refresh")
  ResponseEntity<ApiResponse<TokenResponse>> refresh(
      @Valid @RequestBody RefreshTokenRequest request, HttpServletRequest httpRequest) {
    return ResponseEntity.ok(
        ApiResponse.success(identityService.refresh(request), httpRequest.getRequestId()));
  }
}
