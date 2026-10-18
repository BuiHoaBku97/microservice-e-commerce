package com.ecommerce.payment.services;

import static org.assertj.core.api.Assertions.assertThat;

import com.ecommerce.payment.configs.VnpayProperties;
import com.ecommerce.payment.models.entities.Payment;
import com.ecommerce.payment.models.entities.PaymentTransaction;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class VnpayServiceTest {
  @Test
  void createsSignedSandboxUrlThatCanBeVerified() {
    var properties = new VnpayProperties(true, "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html",
        "TESTCODE", "test-secret", "https://shop.test/vnpay/return",
        "https://shop.test/vnpay/ipn", "https://shop.test/payment/result", 15);
    var service = new VnpayService(properties);
    var payment = new Payment(10L, 20L, 100_000L, "VND", Instant.now().plusSeconds(900), "VNPAY");
    var transaction = new PaymentTransaction(1L, "VNPAY", "PAY-1-abc", 100_000L, "VND");

    String url = service.createUrl(payment, transaction, "127.0.0.1");
    Map<String, String> params = Arrays.stream(URI.create(url).getQuery().split("&"))
        .map(part -> part.split("=", 2))
        .collect(Collectors.toMap(pair -> URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
            pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));

    assertThat(params.get("vnp_Amount")).isEqualTo("10000000");
    assertThat(params.get("vnp_TxnRef")).isEqualTo("PAY-1-abc");
    assertThat(service.validSignature(params)).isTrue();
  }
}
