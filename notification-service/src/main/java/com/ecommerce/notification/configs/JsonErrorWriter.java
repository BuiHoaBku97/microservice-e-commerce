package com.ecommerce.notification.configs;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

final class JsonErrorWriter {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private JsonErrorWriter() {}
  static void write(HttpServletResponse response, int status, String code, String requestId)
      throws IOException {
    response.setStatus(status);
    response.setContentType("application/json");
    MAPPER.writeValue(response.getWriter(),
        new ErrorBody(code, code, null, List.of(), requestId == null ? "" : requestId));
  }
  private record ErrorBody(
      String code, String message, Object data, List<String> errors, String requestId) {}
}

