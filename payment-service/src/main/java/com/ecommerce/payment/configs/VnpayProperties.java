package com.ecommerce.payment.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class VnpayProperties {
  private final boolean enabled;
  private final String payUrl;
  private final String tmnCode;
  private final String hashSecret;
  private final String returnUrl;
  private final String ipnUrl;
  private final String frontendResultUrl;
  private final int expireMinutes;

  public VnpayProperties(
      @Value("${app.payment.vnpay.enabled:false}") boolean enabled,
      @Value("${app.payment.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
      @Value("${app.payment.vnpay.tmn-code:}") String tmnCode,
      @Value("${app.payment.vnpay.hash-secret:}") String hashSecret,
      @Value("${app.payment.vnpay.return-url:}") String returnUrl,
      @Value("${app.payment.vnpay.ipn-url:}") String ipnUrl,
      @Value("${app.payment.vnpay.frontend-result-url:}") String frontendResultUrl,
      @Value("${app.payment.vnpay.expire-minutes:15}") int expireMinutes) {
    this.enabled = enabled; this.payUrl = payUrl; this.tmnCode = tmnCode;
    this.hashSecret = hashSecret; this.returnUrl = returnUrl; this.ipnUrl = ipnUrl;
    this.frontendResultUrl = frontendResultUrl; this.expireMinutes = expireMinutes;
  }
  public boolean enabled() { return enabled; }
  public String payUrl() { return payUrl; }
  public String tmnCode() { return tmnCode; }
  public String hashSecret() { return hashSecret; }
  public String returnUrl() { return returnUrl; }
  public String ipnUrl() { return ipnUrl; }
  public String frontendResultUrl() { return frontendResultUrl; }
  public int expireMinutes() { return expireMinutes; }
}
