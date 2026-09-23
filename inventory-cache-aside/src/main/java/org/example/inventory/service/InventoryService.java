package org.example.inventory.service;

import org.example.inventory.dto.ProductInventoryDTO;
import org.example.inventory.entity.ProductInventory;
import org.example.inventory.exception.InvalidQuantityException;
import org.example.inventory.exception.ProductNotFoundException;
import org.example.inventory.repository.ProductInventoryRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final ProductInventoryRepository repository;

    public InventoryService(ProductInventoryRepository repository) {
        this.repository = repository;
    }

    @Cacheable(value = "inventory", key = "#productId")
    public ProductInventoryDTO getInventory(String productId) {
        validateProductId(productId);

        ProductInventory entity = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        return toDTO(entity);
    }

    @Transactional
    @CacheEvict(value = "inventory", key = "#productId")
    public ProductInventoryDTO updateInventory(String productId, Integer newQuantity) {
        validateProductId(productId);

        if (newQuantity < 0) {
            throw new InvalidQuantityException(newQuantity);
        }

        ProductInventory entity = repository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));

        entity.setQuantity(newQuantity);
        ProductInventory saved = repository.save(entity);

        return toDTO(saved);
    }

    private void validateProductId(String productId) {
        if (productId == null || productId.trim().isEmpty()) {
            throw new IllegalArgumentException("productId không được null hoặc rỗng");
        }
    }

    private ProductInventoryDTO toDTO(ProductInventory entity) {
        return new ProductInventoryDTO(entity.getProductId(), entity.getQuantity());
    }
}
