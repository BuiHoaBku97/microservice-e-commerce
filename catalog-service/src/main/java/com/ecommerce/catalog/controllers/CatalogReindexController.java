package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.entities.CatalogReindexJob;
import com.ecommerce.catalog.services.CatalogReindexService;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/catalog/reindex")
public class CatalogReindexController {
  private final CatalogReindexService catalogReindexService;

  public CatalogReindexController(CatalogReindexService catalogReindexService) {
    this.catalogReindexService = catalogReindexService;
  }

  @PostMapping
  public ResponseEntity<Map<String, Long>> request(Authentication auth) {
    CatalogReindexJob job = catalogReindexService.request(Long.valueOf(auth.getName()));
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("jobId", job.getId()));
  }

  @GetMapping("/{id}")
  public CatalogReindexJob get(@PathVariable Long id) {
    return catalogReindexService.get(id);
  }
}
