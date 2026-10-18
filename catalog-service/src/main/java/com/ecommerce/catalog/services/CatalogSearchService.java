package com.ecommerce.catalog.services;

import com.ecommerce.catalog.models.dtos.responses.SearchProductResponse;
import com.ecommerce.catalog.search.CatalogProductDocument;
import com.ecommerce.catalog.search.CatalogProductSearchRepository;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

@Service
public class CatalogSearchService {
  private final CatalogProductSearchRepository productSearchRepository;
  private final ElasticsearchOperations elasticsearchOperations;

  public CatalogSearchService(
      CatalogProductSearchRepository productSearchRepository,
      ElasticsearchOperations elasticsearchOperations) {
    this.productSearchRepository = productSearchRepository;
    this.elasticsearchOperations = elasticsearchOperations;
  }

  public Page<SearchProductResponse> search(
      String query,
      Long categoryId,
      Long minPrice,
      Long maxPrice,
      String sort,
      int page,
      int size) {
    String normalizedQuery = normalize(query);
    int safeSize = Math.min(Math.max(size, 1), 100);
    int safePage = Math.max(page, 0);
    Sort sortOrder =
        switch (sort == null ? "relevance" : sort) {
          case "price_asc" -> Sort.by("minPrice").ascending();
          case "price_desc" -> Sort.by("minPrice").descending();
          case "newest" -> Sort.by("createdAt").descending();
          default -> Sort.unsorted();
        };
    Criteria criteria = Criteria.where("status").is("ACTIVE");
    if (categoryId != null) {
      criteria = criteria.and("categoryId").is(categoryId);
    }
    if (minPrice != null) {
      criteria = criteria.and("minPrice").greaterThanEqual(minPrice);
    }
    if (maxPrice != null) {
      criteria = criteria.and("minPrice").lessThanEqual(maxPrice);
    }
    if (!normalizedQuery.isBlank()) {
      Criteria textCriteria =
          Criteria.where("name")
              .matches(query)
              .or("description")
              .matches(query);
      for (String term : normalizedQuery.split("\\s+")) {
        textCriteria =
            textCriteria
                .or("normalizedName")
                .contains(term)
                .or("colors")
                .contains(term)
                .or("sizes")
                .contains(term);
      }
      criteria = criteria.subCriteria(textCriteria);
    }
    PageRequest pageable = PageRequest.of(safePage, safeSize, sortOrder);
    var searchHits =
        elasticsearchOperations.search(
            new CriteriaQuery(criteria, pageable), CatalogProductDocument.class);
    List<SearchProductResponse> searchResults =
        searchHits.getSearchHits().stream()
            .map(SearchHit::getContent)
            .map(
                document ->
                    new SearchProductResponse(
                        document.getId(),
                        document.getName(),
                        document.getCategoryId(),
                        document.getImageUrl(),
                        document.getMinPrice(),
                        document.getCurrency(),
                        document.getProductVersion()))
            .toList();
    return new PageImpl<>(searchResults, pageable, searchHits.getTotalHits());
  }

  private String normalize(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}+", "")
        .replace('đ', 'd')
        .replace('Đ', 'D')
        .toLowerCase(Locale.ROOT)
        .trim();
  }
}
