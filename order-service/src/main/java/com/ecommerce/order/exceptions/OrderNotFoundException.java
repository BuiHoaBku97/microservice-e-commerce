package com.ecommerce.order.exceptions;

public class OrderNotFoundException extends RuntimeException {
  public OrderNotFoundException(Long orderId) {
    super("Không tìm thấy đơn hàng: " + orderId);
  }
}
