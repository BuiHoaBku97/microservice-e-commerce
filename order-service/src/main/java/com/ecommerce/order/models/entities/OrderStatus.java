package com.ecommerce.order.models.entities;

public enum OrderStatus {
  PENDING,
  PAID,
  PAYMENT_FAILED,
  PAYMENT_REVIEW,
  PAYMENT_REJECTED,
  CONFIRMED,
  CANCELLED,
  COMPLETED
}
