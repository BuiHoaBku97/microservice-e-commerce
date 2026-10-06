package com.ecommerce.catalog.models.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(columnDefinition = "text")
    private String description;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "image_urls", columnDefinition = "jsonb", nullable = false)
    private List<String> imageUrls = new ArrayList<>();
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CatalogStatus status = CatalogStatus.DRAFT;
    @Version
    @Column(nullable = false)
    private Long version;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Product() { }

    public Product(Category category, String name, String description, List<String> imageUrls) {
        this.category = category;
        this.name = name;
        this.description = description;
        this.imageUrls = new ArrayList<>(imageUrls);
    }

    @PrePersist void onCreate() { Instant now = Instant.now(); createdAt = now; updatedAt = now; }
    @PreUpdate void onUpdate() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public Category getCategory() { return category; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getImageUrls() { return List.copyOf(imageUrls); }
    public CatalogStatus getStatus() { return status; }
    public Long getVersion() { return version; }
    public void update(Category category, String name, String description, List<String> imageUrls) { this.category = category; this.name = name; this.description = description; this.imageUrls = new ArrayList<>(imageUrls); }
    public void changeStatus(CatalogStatus status) { this.status = status; }
}
