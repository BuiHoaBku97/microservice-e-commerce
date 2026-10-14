package com.ecommerce.payment.controllers;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.payment.configs.JwtAuthenticationFilter;
import com.ecommerce.payment.configs.SecurityConfig;
import com.ecommerce.payment.exceptions.PaymentNotFoundException;
import com.ecommerce.payment.models.dtos.responses.PaymentResponse;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.services.PaymentQueryService;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(PaymentController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
class PaymentControllerTest {
  private static final KeyPair KEY_PAIR = createKeyPair();
  @Autowired private MockMvc mockMvc;
  @MockitoBean private PaymentQueryService paymentQueryService;

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.security.jwt.public-key-base64",
        () -> Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded()));
  }

  @Test
  void returnsOwnedPaymentAndIgnoresSpoofedUserId() throws Exception {
    String token = token("CUSTOMER");
    when(paymentQueryService.findByOrder(1L, 10L))
        .thenReturn(PaymentResponse.from(new Payment(10L, 1L, 50000L, "VND")));
    mockMvc
        .perform(
            get("/api/v1/orders/10/payment")
                .header("Authorization", "Bearer " + token)
                .header("X-User-Id", "99")
                .param("userId", "99"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.userId").value(1))
        .andExpect(jsonPath("$.data.amount").value(50000));
    verify(paymentQueryService).findByOrder(1L, 10L);
  }

  @Test
  void returns404WhenPaymentDoesNotExist() throws Exception {
    when(paymentQueryService.findByOrder(1L, 10L)).thenThrow(new PaymentNotFoundException());
    mockMvc
        .perform(
            get("/api/v1/orders/10/payment").header("Authorization", "Bearer " + token("CUSTOMER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
  }

  @Test
  void returns404WithoutLeakingPayment() throws Exception {
    when(paymentQueryService.findByOrder(anyLong(), anyLong()))
        .thenThrow(new PaymentNotFoundException());
    mockMvc
        .perform(
            get("/api/v1/orders/10/payment").header("Authorization", "Bearer " + token("CUSTOMER")))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
        .andExpect(jsonPath("$.data").isEmpty());
  }

  @Test
  void rejectsMissingAndInvalidJwt() throws Exception {
    mockMvc.perform(get("/api/v1/orders/10/payment")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/orders/10/payment").header("Authorization", "Bearer invalid"))
        .andExpect(status().isUnauthorized());
    verifyNoInteractions(paymentQueryService);
  }

  @Test
  void adminCannotBypassCustomerOwnershipApi() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/orders/10/payment").header("Authorization", "Bearer " + token("ADMIN")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(paymentQueryService);
  }

  @Test
  void oldRouteCannotExposePayment() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/payments/orders/10")
                .header("Authorization", "Bearer " + token("CUSTOMER")))
        .andExpect(status().isForbidden());
    verifyNoInteractions(paymentQueryService);
  }

  private String token(String scope) {
    return Jwts.builder()
        .subject("1")
        .issuer("ecommerce-identity")
        .audience()
        .add("ecommerce-api")
        .and()
        .claim("scope", scope)
        .expiration(Date.from(Instant.now().plusSeconds(60)))
        .signWith(KEY_PAIR.getPrivate(), Jwts.SIG.RS256)
        .compact();
  }

  private static KeyPair createKeyPair() {
    try {
      return KeyPairGenerator.getInstance("RSA").generateKeyPair();
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }
}
