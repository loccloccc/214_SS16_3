package org.example.inventory.exception;

public class InvalidQuantityException extends RuntimeException {

    public InvalidQuantityException(Integer quantity) {
        super("Số lượng không hợp lệ: " + quantity + ". Số lượng phải >= 0");
    }
}
