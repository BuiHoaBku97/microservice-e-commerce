package com.ecommerce.catalog.search;

import java.time.Instant;
import java.util.List;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.*;

@Document(indexName = "catalog-products", createIndex = false)
@Setting(settingPath = "elasticsearch/catalog-settings.json")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CatalogProductDocument {
  @Id private Long id;

  @Field(type = FieldType.Text, analyzer = "vietnamese_folding")
  private String name;

  @Field(type = FieldType.Keyword)
  private String normalizedName;

  @Field(type = FieldType.Text, analyzer = "vietnamese_folding")
  private String description;

  @Field(type = FieldType.Long)
  private Long categoryId;

  @Field(type = FieldType.Keyword)
  private String imageUrl;

  @Field(type = FieldType.Keyword)
  private List<String> colors;

  @Field(type = FieldType.Keyword)
  private List<String> sizes;

  @Field(type = FieldType.Long)
  private Long minPrice;

  @Field(type = FieldType.Keyword)
  private String currency;

  @Field(type = FieldType.Long)
  private Long productVersion;

  @Field(type = FieldType.Keyword)
  private String status;

  @Field(type = FieldType.Date, format = DateFormat.date_time)
  private Instant createdAt;
}
