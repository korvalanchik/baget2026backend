package com.example.baget.calculation.preview;

import org.springframework.http.HttpStatus;

public class PreviewException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public PreviewException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }
    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
