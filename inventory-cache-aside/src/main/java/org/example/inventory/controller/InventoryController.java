package org.example.inventory.controller;

import org.example.inventory.dto.ProductInventoryDTO;
import org.example.inventory.exception.InvalidQuantityException;
import org.example.inventory.exception.ProductNotFoundException;
import org.example.inventory.service.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping("/{productId}")
    public ResponseEntity<?> getInventory(@PathVariable String productId) {
        try {
            ProductInventoryDTO dto = inventoryService.getInventory(productId);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ProductNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{productId}")
    public ResponseEntity<?> updateInventory(
            @PathVariable String productId,
            @RequestBody Map<String, Integer> body) {
        try {
            Integer quantity = body.get("quantity");
            if (quantity == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Thiếu trường quantity trong request body"));
            }
            ProductInventoryDTO dto = inventoryService.updateInventory(productId, quantity);
            return ResponseEntity.ok(dto);
        } catch (IllegalArgumentException | InvalidQuantityException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (ProductNotFoundException e) {
            return ResponseEntity.status(404).body(Map.of("error", e.getMessage()));
        }
    }
}
