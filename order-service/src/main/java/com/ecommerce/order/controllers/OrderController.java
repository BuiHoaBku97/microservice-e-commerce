package com.ecommerce.order.controllers;

import com.ecommerce.order.models.dtos.CartDtos;
import com.ecommerce.order.models.entities.Cart;
import com.ecommerce.order.services.CartService;
import com.ecommerce.order.services.InventoryService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {
    private final CartService carts;
    private final InventoryService inventory;
    public OrderController(CartService carts, InventoryService inventory) { this.carts = carts; this.inventory = inventory; }

    @GetMapping("/cart")
    public CartDtos.CartResponse cart(@AuthenticationPrincipal Jwt jwt) { return response(carts.getOrCreate(userId(jwt))); }

    @PostMapping("/cart/items")
    public CartDtos.CartResponse add(@AuthenticationPrincipal Jwt jwt, @RequestParam Long version, @Valid @RequestBody CartDtos.CartItemRequest request) { return response(carts.add(userId(jwt), request, version)); }

    @PatchMapping("/cart/items/{skuId}")
    public CartDtos.CartResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long skuId, @RequestParam Long version, @Valid @RequestBody CartDtos.CartItemRequest request) { return response(carts.update(userId(jwt), skuId, request, version)); }

    @DeleteMapping("/cart/items/{skuId}")
    public CartDtos.CartResponse remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long skuId, @RequestParam Long version) { return response(carts.remove(userId(jwt), skuId, version)); }

    @DeleteMapping("/cart")
    public ResponseEntity<Void> clear(@AuthenticationPrincipal Jwt jwt, @RequestParam Long version) { carts.clear(userId(jwt), version); return ResponseEntity.noContent().build(); }

    @GetMapping("/inventory/availability")
    public List<CartDtos.InventoryResponse> availability(@RequestParam List<Long> skuIds) { if (skuIds.size() > 100) throw new IllegalArgumentException("Chỉ được kiểm tra tối đa 100 SKU"); return inventory.availability(skuIds); }

    @PostMapping("/admin/inventory/{skuId}/initialize")
    public CartDtos.InventoryResponse initialize(@PathVariable Long skuId) { return toInventory(inventory.initialize(skuId)); }

    @PostMapping("/admin/inventory/{skuId}/receipts")
    public CartDtos.InventoryResponse receipt(@PathVariable Long skuId, @Valid @RequestBody CartDtos.InventoryMutationRequest request) { return toInventory(inventory.receipt(skuId, request)); }

    @PostMapping("/admin/inventory/{skuId}/adjustments")
    public CartDtos.InventoryResponse adjustment(@PathVariable Long skuId, @Valid @RequestBody CartDtos.InventoryMutationRequest request) { return toInventory(inventory.adjustment(skuId, request)); }

    private Long userId(Jwt jwt) { return Long.valueOf(jwt.getSubject()); }
    private CartDtos.CartResponse response(Cart cart) { return new CartDtos.CartResponse(cart.getId(), cart.getUserId(), cart.getVersion(), cart.getItems().stream().map(i -> new CartDtos.CartResponse.Item(i.getSkuId(), i.getQuantity(), i.getDisplayedUnitPrice())).toList()); }
    private CartDtos.InventoryResponse toInventory(com.ecommerce.order.models.entities.Inventory i) { return new CartDtos.InventoryResponse(i.getSkuId(), i.getOnHand(), i.getReserved(), i.getOnHand() - i.getReserved(), i.getVersion()); }
}
