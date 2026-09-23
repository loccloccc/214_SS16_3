package org.example.inventory.repository;

import org.example.inventory.entity.ProductInventory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductInventoryRepository extends JpaRepository<ProductInventory, String> {
}
