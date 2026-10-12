package com.ecommerce.order.exceptions;

public class PriceChangedException extends RuntimeException {
  public PriceChangedException() {
    super("Giá sản phẩm đã thay đổi, vui lòng kiểm tra lại giỏ hàng");
  }
}
