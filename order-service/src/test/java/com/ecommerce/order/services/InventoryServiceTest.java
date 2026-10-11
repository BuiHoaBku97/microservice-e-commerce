package com.ecommerce.order.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Inventory;
import com.ecommerce.order.repositories.InventoryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {
  @Test
  void availabilityShouldSubtractReservedDeduplicateAndReturnZeroForMissingSku() {
    InventoryRepository repository = mock(InventoryRepository.class);
    Inventory inventory = new Inventory();
    inventory.setSkuId(1L);
    inventory.setOnHand(10);
    inventory.setReserved(3);
    when(repository.findBySkuIdIn(List.of(1L, 2L, 1L))).thenReturn(List.of(inventory));

    assertThat(new InventoryService(repository).availability(List.of(1L, 2L, 1L)))
        .containsExactly(
            new CartDtos.InventoryAvailabilityResponse(1L, 7),
            new CartDtos.InventoryAvailabilityResponse(2L, 0));
  }
}

