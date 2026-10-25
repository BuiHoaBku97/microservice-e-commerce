package com.ecommerce.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GitHubWebhookSignatureFilter extends OncePerRequestFilter {

  private static final String MONITOR_PATH = "/monitor";
  private static final String SIGNATURE_HEADER = "X-Hub-Signature-256";
  private final String webhookSecret;

  public GitHubWebhookSignatureFilter(
      @Value("${CONFIG_WEBHOOK_SECRET:}") String webhookSecret) {
    this.webhookSecret = webhookSecret;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (!MONITOR_PATH.equals(request.getRequestURI()) || webhookSecret.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }

    byte[] body = request.getInputStream().readAllBytes();
    String providedSignature = request.getHeader(SIGNATURE_HEADER);
    if (!isValidSignature(body, providedSignature)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid webhook signature");
      return;
    }

    filterChain.doFilter(new ReplayableRequest(request, body), response);
  }

  private boolean isValidSignature(byte[] body, String providedSignature) {
    if (providedSignature == null || !providedSignature.startsWith("sha256=")) {
      return false;
    }

    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      String expected = "sha256=" + toHex(mac.doFinal(body));
      return MessageDigest.isEqual(
          expected.getBytes(StandardCharsets.US_ASCII),
          providedSignature.getBytes(StandardCharsets.US_ASCII));
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
      throw new IllegalStateException("HMAC-SHA256 is not available", exception);
    }
  }

  private String toHex(byte[] bytes) {
    StringBuilder result = new StringBuilder(bytes.length * 2);
    for (byte value : bytes) {
      result.append(String.format("%02x", value));
    }
    return result.toString();
  }

  private static final class ReplayableRequest extends HttpServletRequestWrapper {
    private final byte[] body;

    private ReplayableRequest(HttpServletRequest request, byte[] body) {
      super(request);
      this.body = body;
    }

    @Override
    public ServletInputStream getInputStream() {
      ByteArrayInputStream input = new ByteArrayInputStream(body);
      return new ServletInputStream() {
        @Override
        public int read() {
          return input.read();
        }

        @Override
        public boolean isFinished() {
          return input.available() == 0;
        }

        @Override
        public boolean isReady() {
          return true;
        }

        @Override
        public void setReadListener(jakarta.servlet.ReadListener readListener) {
          // Synchronous request processing does not need a read listener.
        }
      };
    }
  }
}
