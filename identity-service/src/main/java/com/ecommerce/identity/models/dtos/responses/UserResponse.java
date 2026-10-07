package com.ecommerce.identity.models.dtos.responses;

import com.ecommerce.identity.models.entities.RoleName;
import com.ecommerce.identity.models.entities.UserStatus;
import java.util.Set;

public record UserResponse(
    Long id,
    String email,
    String fullName,
    String phone,
    UserStatus status,
    Set<RoleName> roles,
    Long version) {}
