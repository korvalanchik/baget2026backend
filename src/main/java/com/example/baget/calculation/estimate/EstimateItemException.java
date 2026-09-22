package com.example.baget.calculation.estimate;

import com.example.baget.calculation.preview.PreviewException;

public class EstimateItemException extends PreviewException {
    private final String clientItemId;

    public EstimateItemException(String clientItemId, PreviewException cause) {
        super(cause.getStatus(), cause.getCode(), cause.getMessage());
        this.clientItemId = clientItemId;
        initCause(cause);
    }

    public String getClientItemId() { return clientItemId; }
}
