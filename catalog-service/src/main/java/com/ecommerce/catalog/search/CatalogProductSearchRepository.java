package com.ecommerce.catalog.search;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface CatalogProductSearchRepository
    extends ElasticsearchRepository<CatalogProductDocument, Long> {}
