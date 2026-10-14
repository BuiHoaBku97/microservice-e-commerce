package com.ecommerce.catalog.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ecommerce.catalog.search.CatalogProductDocument;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;

class CatalogIndexManagerTest {

  @Test
  void doesNotOverwriteANewerDocument() {
    ElasticsearchOperations operations = org.mockito.Mockito.mock(ElasticsearchOperations.class);
    CatalogProductDocument incoming = document(4L);
    when(operations.get(eq("7"), eq(CatalogProductDocument.class), any(IndexCoordinates.class)))
        .thenReturn(document(5L));

    new CatalogIndexManager(operations).saveIfNewer(incoming, "catalog-products-next");

    verify(operations, never()).save(eq(incoming), any(IndexCoordinates.class));
  }

  @Test
  void switchesAliasWithOneAtomicAliasRequest() {
    ElasticsearchOperations operations = org.mockito.Mockito.mock(ElasticsearchOperations.class);
    IndexOperations definition = org.mockito.Mockito.mock(IndexOperations.class);
    IndexOperations target = org.mockito.Mockito.mock(IndexOperations.class);
    when(operations.indexOps(CatalogProductDocument.class)).thenReturn(definition);
    when(operations.indexOps(any(IndexCoordinates.class))).thenReturn(target);
    when(definition.getAliases("catalog-products"))
        .thenReturn((Map) Map.of("catalog-products-old", Set.of()));

    new CatalogIndexManager(operations).switchAlias("catalog-products", "catalog-products-next");

    verify(target).alias(any(AliasActions.class));
  }

  private CatalogProductDocument document(long version) {
    return new CatalogProductDocument(
        7L,
        "Áo",
        "ao",
        "description",
        1L,
        null,
        List.of("black"),
        List.of("M"),
        100L,
        "VND",
        version,
        "ACTIVE",
        Instant.now());
  }
}
