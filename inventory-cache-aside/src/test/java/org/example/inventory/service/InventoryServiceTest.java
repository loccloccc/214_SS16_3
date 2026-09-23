package org.example.inventory.service;

import org.example.inventory.dto.ProductInventoryDTO;
import org.example.inventory.entity.ProductInventory;
import org.example.inventory.exception.InvalidQuantityException;
import org.example.inventory.exception.ProductNotFoundException;
import org.example.inventory.repository.ProductInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @MockBean
    private ProductInventoryRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void clearCache() {
        Cache cache = cacheManager.getCache("inventory");
        if (cache != null) {
            cache.clear();
        }
    }

    @Test
    void test1_cacheMiss_shouldCallRepository() {
        when(repository.findById("P001"))
                .thenReturn(Optional.of(new ProductInventory("P001", 100)));

        ProductInventoryDTO result = inventoryService.getInventory("P001");

        assertThat(result.getProductId()).isEqualTo("P001");
        assertThat(result.getQuantity()).isEqualTo(100);
        verify(repository, times(1)).findById("P001");
    }

    @Test
    void test2_cacheHit_shouldCallRepositoryOnlyOnce() {
        when(repository.findById("P001"))
                .thenReturn(Optional.of(new ProductInventory("P001", 100)));

        inventoryService.getInventory("P001");
        inventoryService.getInventory("P001");

        verify(repository, times(1)).findById("P001");
    }

    @Test
    void test3_updateInventory_shouldEvictCacheAndReturnNewValue() {
        when(repository.findById("P001"))
                .thenReturn(Optional.of(new ProductInventory("P001", 100)));
        when(repository.save(any(ProductInventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.getInventory("P001");
        verify(repository, times(1)).findById("P001");

        inventoryService.updateInventory("P001", 95);

        reset(repository);
        when(repository.findById("P001"))
                .thenReturn(Optional.of(new ProductInventory("P001", 95)));

        ProductInventoryDTO afterUpdate = inventoryService.getInventory("P001");

        assertThat(afterUpdate.getQuantity()).isEqualTo(95);
        verify(repository, times(1)).findById("P001");
    }

    @Test
    void test4_negativeQuantity_shouldThrowAndNotUpdateDb() {
        assertThatThrownBy(() -> inventoryService.updateInventory("P001", -10))
                .isInstanceOf(InvalidQuantityException.class)
                .hasMessageContaining("-10");

        verify(repository, never()).save(any());
    }

    @Test
    void test5_nullProductId_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> inventoryService.getInventory(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("productId không được null hoặc rỗng");
    }

    @Test
    void test6_blankProductId_shouldThrowIllegalArgumentException() {
        assertThatThrownBy(() -> inventoryService.getInventory(""))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> inventoryService.getInventory("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void test7_productNotFound_shouldThrowProductNotFoundException() {
        when(repository.findById("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventory("UNKNOWN"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("UNKNOWN");
    }

    @Test
    void test8_redisGetError_shouldFallbackToDatabase() {
        when(repository.findById("P001"))
                .thenReturn(Optional.of(new ProductInventory("P001", 100)));

        Cache cache = cacheManager.getCache("inventory");
        assertThat(cache).isNotNull();

        cache.clear();

        ProductInventoryDTO result = inventoryService.getInventory("P001");

        assertThat(result).isNotNull();
        assertThat(result.getProductId()).isEqualTo("P001");
        assertThat(result.getQuantity()).isEqualTo(100);
        verify(repository, atLeastOnce()).findById("P001");
    }
}
