package com.ecommerce.order.models.entities;

public enum OrderStatus {
  PENDING_PAYMENT,
  PAYMENT_REVIEW,
  CONFIRMED,
  SHIPPING,
  CANCELLED,
  COMPLETED
}
