package com.ecommerce.catalog.models.dtos.responses;

import java.util.List;
import org.springframework.data.domain.Page;

public record PageResponse<T>(
    List<T> items, int page, int size, long totalElements, int totalPages) {
  public static <T> PageResponse<T> from(Page<T> value) {
    return new PageResponse<>(value.getContent(), value.getNumber(), value.getSize(),
        value.getTotalElements(), value.getTotalPages());
  }
}
