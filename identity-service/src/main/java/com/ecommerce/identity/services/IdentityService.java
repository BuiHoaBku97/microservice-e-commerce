package com.ecommerce.identity.services;

import com.ecommerce.identity.models.dtos.requests.LoginRequest;
import com.ecommerce.identity.models.dtos.requests.RefreshTokenRequest;
import com.ecommerce.identity.models.dtos.requests.RegisterRequest;
import com.ecommerce.identity.models.dtos.responses.TokenResponse;
import com.ecommerce.identity.models.dtos.responses.UserResponse;

public interface IdentityService {
  UserResponse register(RegisterRequest request);

  TokenResponse login(LoginRequest request);

  TokenResponse refresh(RefreshTokenRequest request);

  void logout(RefreshTokenRequest request);
}
