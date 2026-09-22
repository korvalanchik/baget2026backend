package com.example.baget.util;

public class CustomOptimisticLockException extends RuntimeException {

    public CustomOptimisticLockException(String message) {
        super(message);
    }

    public CustomOptimisticLockException(String message, Throwable cause) {
        super(message, cause);
    }
}