package com.ecommerce.catalog.services;

import com.ecommerce.catalog.search.CatalogProductDocument;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.index.AliasAction;
import org.springframework.data.elasticsearch.core.index.AliasActionParameters;
import org.springframework.data.elasticsearch.core.index.AliasActions;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;

@Component
public class CatalogIndexManager {
  public static final String ALIAS = "catalog-products";

  private final ElasticsearchOperations elasticsearchOperations;

  public CatalogIndexManager(ElasticsearchOperations elasticsearchOperations) {
    this.elasticsearchOperations = elasticsearchOperations;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void initializeServingIndex() {
    try {
      ensureServingIndex();
    } catch (RuntimeException ignored) {
      // The durable catalog outbox retries initialization when Elasticsearch recovers.
    }
  }

  public void ensureServingIndex() {
    if (!indicesForAlias(ALIAS).isEmpty()) {
      return;
    }
    String initialIndex = ALIAS + "-" + System.currentTimeMillis();
    createIndex(initialIndex);
    addAlias(initialIndex, ALIAS);
  }

  public void createIndex(String indexName) {
    IndexOperations target = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
    if (target.exists()) {
      return;
    }
    IndexOperations definition = elasticsearchOperations.indexOps(CatalogProductDocument.class);
    target.create(
        definition.createSettings(CatalogProductDocument.class),
        definition.createMapping(CatalogProductDocument.class));
  }

  public void saveIfNewer(CatalogProductDocument document, String indexName) {
    IndexCoordinates coordinates = IndexCoordinates.of(indexName);
    CatalogProductDocument current =
        elasticsearchOperations.get(
            document.getId().toString(), CatalogProductDocument.class, coordinates);
    if (current == null || current.getProductVersion() <= document.getProductVersion()) {
      elasticsearchOperations.save(document, coordinates);
    }
  }

  public void delete(Long productId, String indexName) {
    elasticsearchOperations.delete(productId.toString(), IndexCoordinates.of(indexName));
  }

  public void switchAlias(String alias, String targetIndex) {
    List<AliasAction> actions = new ArrayList<>();
    for (String currentIndex : indicesForAlias(alias).keySet()) {
      if (!currentIndex.equals(targetIndex)) {
        actions.add(new AliasAction.Remove(parameters(currentIndex, alias)));
      }
    }
    if (!indicesForAlias(alias).containsKey(targetIndex)) {
      actions.add(new AliasAction.Add(parameters(targetIndex, alias)));
    }
    if (!actions.isEmpty()) {
      elasticsearchOperations
          .indexOps(IndexCoordinates.of(targetIndex))
          .alias(new AliasActions(actions.toArray(AliasAction[]::new)));
    }
  }

  private void addAlias(String indexName, String alias) {
    elasticsearchOperations
        .indexOps(IndexCoordinates.of(indexName))
        .alias(new AliasActions(new AliasAction.Add(parameters(indexName, alias))));
  }

  private Map<String, ?> indicesForAlias(String alias) {
    try {
      return elasticsearchOperations.indexOps(CatalogProductDocument.class).getAliases(alias);
    } catch (RuntimeException exception) {
      if (exception instanceof NoSuchIndexException
          || exception instanceof ResourceNotFoundException) {
        return Map.of();
      }
      throw exception;
    }
  }

  private AliasActionParameters parameters(String indexName, String alias) {
    return AliasActionParameters.builder().withIndices(indexName).withAliases(alias).build();
  }
}
