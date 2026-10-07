package com.ecommerce.order.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "cart_items", uniqueConstraints = @UniqueConstraint(name = "uk_cart_items_sku", columnNames = {"cart_id", "sku_id"}))
@Getter
@Setter
@NoArgsConstructor
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

    public void touch() { if (createdAt == null) createdAt = Instant.now(); updatedAt = Instant.now(); }
}
