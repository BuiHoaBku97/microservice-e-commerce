package com.ecommerce.order.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {
  @Id private Long skuId;
  private Integer onHand = 0;
  private Integer reserved = 0;
  private Long version = 0L;
  private Instant createdAt;
  private Instant updatedAt;
}
