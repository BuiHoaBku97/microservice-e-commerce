package com.ecommerce.order.exceptions;

public class CatalogUnavailableException extends RuntimeException {
  public CatalogUnavailableException() {
    super("Tạm thời không thể xác minh giá sản phẩm, vui lòng thử lại sau");
  }
}
