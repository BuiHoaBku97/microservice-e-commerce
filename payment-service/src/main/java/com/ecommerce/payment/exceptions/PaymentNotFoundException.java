package com.ecommerce.payment.exceptions;

public class PaymentNotFoundException extends RuntimeException {
  public PaymentNotFoundException() {
    super("Không tìm thấy thanh toán cho đơn hàng");
  }
}
