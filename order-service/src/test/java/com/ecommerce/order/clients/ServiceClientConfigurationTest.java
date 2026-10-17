package com.ecommerce.order.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.order.configs.ServiceJwtProvider;
import com.ecommerce.order.configs.CorrelationContext;
import feign.RequestTemplate;
import java.util.Collection;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;

class ServiceClientConfigurationTest {
  @AfterEach
  void clearCorrelation() {
    CorrelationContext.clear();
  }

  @Test
  void identityStatusClientUsesIdentityStatusScope() {
    ServiceJwtProvider provider = mock(ServiceJwtProvider.class);
    when(provider.issue("identity-service", "identity.status")).thenReturn("status-token");
    RequestTemplate template = new RequestTemplate();
    CorrelationContext.set("identity-correlation");

    new IdentityStatusClientConfiguration()
        .identityStatusServiceTokenInterceptor(provider)
        .apply(template);

    assertThat(template.headers().get("X-Service-Authorization"))
        .containsExactly("Bearer status-token");
    assertThat(template.headers().get(CorrelationContext.HEADER))
        .containsExactly("identity-correlation");
    verify(provider).issue("identity-service", "identity.status");
  }

  @Test
  void catalogClientUsesOnlyCatalogServiceCredential() throws Exception {
    ServiceJwtProvider provider = mock(ServiceJwtProvider.class);
    when(provider.issue("catalog-service", "catalog.checkout")).thenReturn("catalog-token");
    RequestTemplate template = new RequestTemplate();
    CorrelationContext.set("catalog-correlation");

    new CatalogClientConfiguration().catalogServiceTokenInterceptor(provider).apply(template);

    Map<String, Collection<String>> headers = template.headers();
    assertThat(headers.get("X-Service-Authorization")).containsExactly("Bearer catalog-token");
    assertThat(headers.get(CorrelationContext.HEADER)).containsExactly("catalog-correlation");
    assertThat(headers).doesNotContainKey("Authorization");
    assertThat(
            CatalogClient.class
                .getMethod("getCheckoutItems", CatalogCheckoutRequest.class)
                .getParameterCount())
        .isEqualTo(1);
  }

  @Test
  void catalogReadClientUsesCatalogReadScope() {
    ServiceJwtProvider provider = mock(ServiceJwtProvider.class);
    when(provider.issue("catalog-service", "catalog.read")).thenReturn("catalog-read-token");
    RequestTemplate template = new RequestTemplate();

    new CatalogReadClientConfiguration()
        .catalogReadServiceTokenInterceptor(provider)
        .apply(template);

    assertThat(template.headers().get("X-Service-Authorization"))
        .containsExactly("Bearer catalog-read-token");
    verify(provider).issue("catalog-service", "catalog.read");
  }
}
