package com.ecommerce.order.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_sku", columnNames = {"cart_id", "sku_id"}))
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;
    private Long skuId;
    private Integer quantity;
    private Long displayedUnitPrice;
    private Instant createdAt;
    private Instant updatedAt;

    public Long getId() { return id; }
    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }
    public Long getSkuId() { return skuId; }
    public void setSkuId(Long skuId) { this.skuId = skuId; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Long getDisplayedUnitPrice() { return displayedUnitPrice; }
    public void setDisplayedUnitPrice(Long displayedUnitPrice) { this.displayedUnitPrice = displayedUnitPrice; }
    public void touch() { if (createdAt == null) createdAt = Instant.now(); updatedAt = Instant.now(); }
}
