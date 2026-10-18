package com.ecommerce.payment.controllers;

import com.ecommerce.payment.services.VnpayCallbackService;
import com.ecommerce.payment.services.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/payments/vnpay")
public class VnpayController {
  private final VnpayCallbackService callbackService;
  private final VnpayService vnpayService;

  public VnpayController(VnpayCallbackService callbackService, VnpayService vnpayService) {
    this.callbackService = callbackService; this.vnpayService = vnpayService;
  }

  @GetMapping("/ipn")
  public ResponseEntity<Map<String, String>> ipn(HttpServletRequest request) {
    String code = callbackService.process(parameters(request));
    return ResponseEntity.ok(Map.of("RspCode", code,
        "Message", "00".equals(code) ? "Confirm Success" : "Confirm Failed"));
  }

  @GetMapping("/return")
  public ResponseEntity<Void> returned(HttpServletRequest request) {
    Map<String, String> params = parameters(request);
    String code = vnpayService.validSignature(params) ? params.getOrDefault("vnp_ResponseCode", "99") : "97";
    String target = vnpayService.frontendResultUrl();
    if (target == null || target.isBlank()) return ResponseEntity.noContent().build();
    URI location = UriComponentsBuilder.fromUriString(target)
        .queryParam("orderId", params.get("vnp_TxnRef"))
        .queryParam("status", "00".equals(code) ? "success" : "failed")
        .queryParam("code", code).build(true).toUri();
    return ResponseEntity.status(302).location(location).build();
  }

  private Map<String, String> parameters(HttpServletRequest request) {
    Map<String, String> params = new LinkedHashMap<>();
    request.getParameterMap().forEach((key, values) -> { if (values.length > 0) params.put(key, values[0]); });
    return params;
  }
}
