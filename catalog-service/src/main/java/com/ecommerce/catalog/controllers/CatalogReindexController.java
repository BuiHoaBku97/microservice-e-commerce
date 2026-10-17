package com.ecommerce.catalog.controllers;

import com.ecommerce.catalog.models.entities.CatalogReindexJob;
import com.ecommerce.catalog.models.dtos.responses.ApiResponse;
import com.ecommerce.catalog.services.CatalogReindexService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/search/reindex")
public class CatalogReindexController {
  private final CatalogReindexService catalogReindexService;

  public CatalogReindexController(CatalogReindexService catalogReindexService) {
    this.catalogReindexService = catalogReindexService;
  }

  @PostMapping
  public ResponseEntity<ApiResponse<Map<String, Long>>> request(Authentication auth, HttpServletRequest request) {
    CatalogReindexJob job = catalogReindexService.request(Long.valueOf(auth.getName()));
    return ResponseEntity.status(HttpStatus.ACCEPTED)
        .body(ApiResponse.success(Map.of("jobId", job.getId()), request.getRequestId()));
  }

  @GetMapping("/{id}")
  public ApiResponse<CatalogReindexJob> get(@PathVariable Long id, HttpServletRequest request) {
    return ApiResponse.success(catalogReindexService.get(id), request.getRequestId());
  }
}
