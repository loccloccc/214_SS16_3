package org.example.inventory.exception;

public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String productId) {
        super("Không tìm thấy sản phẩm với id: " + productId);
    }
}
