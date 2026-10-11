package com.ecommerce.order.controllers;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.order.configs.JwtAuthenticationFilter;
import com.ecommerce.order.configs.SecurityConfig;
import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Inventory;
import com.ecommerce.order.services.CartService;
import com.ecommerce.order.services.InventoryService;
import com.ecommerce.order.services.OrderService;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class InventoryAvailabilityControllerTest {
  private static final KeyPair KEY_PAIR = createKeyPair();

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CartService cartService;
  @MockitoBean private InventoryService inventoryService;
  @MockitoBean private OrderService orderService;

  @DynamicPropertySource
  static void jwtProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.security.jwt.public-key-base64",
        () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
  }

  @Test
  void shouldAllowAnonymousReadWithoutExposingInternalStockFields() throws Exception {
    when(inventoryService.availability(List.of(1L)))
        .thenReturn(List.of(new CartDtos.InventoryAvailabilityResponse(1L, 7)));
    mockMvc.perform(get("/api/v1/inventory/availability").param("skuIds", "1"))
        .andExpect(status().isOk())
        .andExpect(content().json("[{\"skuId\":1,\"available\":7}]", true));
  }

  @Test
  void shouldRejectMoreThan100SkuIdsBeforeQueryingInventory() throws Exception {
    String ids = LongStream.rangeClosed(1, 101).mapToObj(Long::toString)
        .collect(Collectors.joining(","));
    mockMvc.perform(get("/api/v1/inventory/availability").param("skuIds", ids))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void shouldRejectNonPositiveSkuId() throws Exception {
    mockMvc.perform(get("/api/v1/inventory/availability").param("skuIds", "0"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void shouldRejectAnonymousInventoryWrite() throws Exception {
    mockMvc.perform(post("/api/v1/admin/inventory/1/initialize"))
        .andExpect(status().is4xxClientError());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void shouldRejectCustomerInventoryWrite() throws Exception {
    mockMvc.perform(post("/api/v1/admin/inventory/1/initialize")
            .header("Authorization", "Bearer " + token("CUSTOMER")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(inventoryService);
  }

  @Test
  void shouldAllowAdminInventoryWrite() throws Exception {
    Inventory inventory = new Inventory();
    inventory.setSkuId(1L);
    when(inventoryService.initialize(anyLong())).thenReturn(inventory);
    mockMvc.perform(post("/api/v1/admin/inventory/1/initialize")
            .header("Authorization", "Bearer " + token("ADMIN")))
        .andExpect(status().isOk());
  }

  private static String token(String scope) {
    return Jwts.builder().subject("1").claim("scope", scope)
        .expiration(Date.from(Instant.now().plusSeconds(60)))
        .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256).compact();
  }

  private static KeyPair createKeyPair() {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      return generator.generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}

