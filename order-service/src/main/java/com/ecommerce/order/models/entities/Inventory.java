package com.ecommerce.order.models.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
public class Inventory {
    @Id
    private Long skuId;
    private Integer onHand = 0;
    private Integer reserved = 0;
    private Long version = 0L;
    private Instant createdAt;
    private Instant updatedAt;

}
