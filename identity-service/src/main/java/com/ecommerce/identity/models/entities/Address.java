package com.ecommerce.identity.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "addresses")
@Getter
@NoArgsConstructor
public class Address {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "recipient_name", nullable = false, length = 150)
  private String recipientName;

  @Column(name = "recipient_phone", nullable = false, length = 30)
  private String recipientPhone;

  @Column(name = "address_line", nullable = false, length = 255)
  private String addressLine;

  @Column(nullable = false, length = 100)
  private String ward;

  @Column(length = 100)
  private String district;

  @Column(nullable = false, length = 100)
  private String province;

  @Column(name = "is_default", nullable = false)
  private boolean defaultAddress;

  @Version
  @Column(nullable = false)
  private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Address(
      Long userId,
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province,
      boolean defaultAddress) {
    this.userId = userId;
    this.update(
        recipientName,
        recipientPhone,
        addressLine,
        ward,
        district,
        province,
        defaultAddress);
  }

  public void update(
      String recipientName,
      String recipientPhone,
      String addressLine,
      String ward,
      String district,
      String province,
      boolean defaultAddress) {
    this.recipientName = recipientName.trim();
    this.recipientPhone = recipientPhone.trim();
    this.addressLine = addressLine.trim();
    this.ward = ward.trim();
    this.district = district == null ? null : district.trim();
    this.province = province.trim();
    this.defaultAddress = defaultAddress;
  }

  public void setDefaultAddress(boolean defaultAddress) {
    this.defaultAddress = defaultAddress;
  }

  @PrePersist
  void onCreate() {
    Instant now = Instant.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }
}
