package com.ecommerce.catalog.configs;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {
  @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    String supplied = request.getHeader(CorrelationContext.HEADER);
    String id = CorrelationContext.isValid(supplied) ? supplied : UUID.randomUUID().toString();
    CorrelationContext.set(id); request.setAttribute("correlationId", id); response.setHeader(CorrelationContext.HEADER, id);
    HttpServletRequestWrapper wrapped = new HttpServletRequestWrapper(request) {
      @Override public String getRequestId() { return id; }
    };
    try { chain.doFilter(wrapped, response); } finally { CorrelationContext.clear(); }
  }
}
