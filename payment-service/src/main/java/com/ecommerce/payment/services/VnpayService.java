package com.ecommerce.payment.services;

import com.ecommerce.payment.configs.VnpayProperties;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentTransaction;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class VnpayService {
  private static final DateTimeFormatter VNPAY_TIME =
      DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
  private final VnpayProperties properties;

  public VnpayService(VnpayProperties properties) { this.properties = properties; }

  public String createUrl(Payment payment, PaymentTransaction transaction, String clientIp) {
    requireConfigured();
    Instant now = Instant.now();
    Map<String, String> fields = new TreeMap<>();
    fields.put("vnp_Amount", Long.toString(payment.getAmount() * 100));
    fields.put("vnp_Command", "pay");
    fields.put("vnp_CreateDate", VNPAY_TIME.format(now));
    fields.put("vnp_CurrCode", "VND");
    fields.put("vnp_ExpireDate", VNPAY_TIME.format(now.plusSeconds(properties.expireMinutes() * 60L)));
    fields.put("vnp_IpAddr", clientIp == null || clientIp.isBlank() ? "127.0.0.1" : clientIp);
    fields.put("vnp_Locale", "vn");
    fields.put("vnp_OrderInfo", "Thanh toan don hang " + payment.getOrderId());
    fields.put("vnp_OrderType", "other");
    fields.put("vnp_ReturnUrl", properties.returnUrl());
    fields.put("vnp_TmnCode", properties.tmnCode());
    fields.put("vnp_TxnRef", transaction.getTxnRef());
    fields.put("vnp_Version", "2.1.0");
    String signData = query(fields);
    return properties.payUrl() + "?" + signData + "&vnp_SecureHash=" + hmac(signData);
  }

  public boolean validSignature(Map<String, String> params) {
    String received = params.get("vnp_SecureHash");
    if (received == null || properties.hashSecret().isBlank()) return false;
    Map<String, String> fields = new TreeMap<>(params);
    fields.remove("vnp_SecureHash");
    fields.remove("vnp_SecureHashType");
    return MessageDigest.isEqual(received.getBytes(StandardCharsets.UTF_8),
        hmac(query(fields)).getBytes(StandardCharsets.UTF_8));
  }

  public String frontendResultUrl() { return properties.frontendResultUrl(); }
  public String tmnCode() { return properties.tmnCode(); }

  private String query(Map<String, String> fields) {
    return fields.entrySet().stream()
        .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
        .reduce((a, b) -> a + "&" + b).orElse("");
  }
  private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  private String hmac(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA512");
      mac.init(new SecretKeySpec(properties.hashSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
      return java.util.HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) { throw new IllegalStateException("VNPAY_SIGN_FAILED", e); }
  }
  private void requireConfigured() {
    if (!properties.enabled() || properties.tmnCode().isBlank() || properties.hashSecret().isBlank()
        || properties.returnUrl().isBlank() || properties.ipnUrl().isBlank())
      throw new IllegalStateException("VNPAY_NOT_CONFIGURED");
  }
}
