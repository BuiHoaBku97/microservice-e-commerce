package com.ecommerce.order.configs;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String supplied = request.getHeader(CorrelationContext.HEADER);
    String correlationId =
        CorrelationContext.isValid(supplied) ? supplied : UUID.randomUUID().toString();
    CorrelationContext.set(correlationId);
    request.setAttribute("correlationId", correlationId);
    response.setHeader(CorrelationContext.HEADER, correlationId);
    HttpServletRequestWrapper correlatedRequest =
        new HttpServletRequestWrapper(request) {
          @Override
          public String getRequestId() {
            return correlationId;
          }
        };
    try {
      chain.doFilter(correlatedRequest, response);
    } finally {
      CorrelationContext.clear();
    }
  }
}
