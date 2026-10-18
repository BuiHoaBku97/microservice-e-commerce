package com.ecommerce.notification.controllers;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ecommerce.notification.exceptions.GlobalExceptionHandler;
import com.ecommerce.notification.configs.CorrelationIdFilter;
import com.ecommerce.notification.models.entities.Notification;
import com.ecommerce.notification.services.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationControllerTest {
  private NotificationService service;
  private MockMvc mockMvc;
  private final UsernamePasswordAuthenticationToken customer =
      UsernamePasswordAuthenticationToken.authenticated("7", null, java.util.List.of());

  @BeforeEach
  void setUp() {
    service = Mockito.mock(NotificationService.class);
    mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(service))
        .setControllerAdvice(new GlobalExceptionHandler())
        .addFilters(new CorrelationIdFilter()).build();
  }

  @Test
  void returnsContractPageEnvelope() throws Exception {
    Notification notification = new Notification(
        7L, "PaymentCompleted", "Paid", "Order paid", "ORDER", 10L, "event-10");
    when(service.listByUser(eq(7L), Mockito.any(Pageable.class)))
        .thenReturn(new PageImpl<>(java.util.List.of(notification)));

    mockMvc.perform(get("/api/v1/notifications").param("page", "0").param("size", "20")
            .principal(customer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("SUCCESS"))
        .andExpect(jsonPath("$.data.items[0].referenceId").value(10))
        .andExpect(jsonPath("$.data.page").value(0))
        .andExpect(jsonPath("$.data.totalElements").value(1))
        .andExpect(jsonPath("$.requestId").isNotEmpty());
  }

  @Test
  void hidesAnotherUsersNotificationAsNotFound() throws Exception {
    when(service.markRead(7L, 99L))
        .thenThrow(new IllegalArgumentException("NOTIFICATION_NOT_FOUND"));

    mockMvc.perform(patch("/api/v1/notifications/99/read").principal(customer))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"))
        .andExpect(jsonPath("$.errors").isArray());
  }
}
