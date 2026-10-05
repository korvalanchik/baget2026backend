package com.example.baget.orderproduct;

import com.example.baget.calculation.estimate.EstimatePreviewRequest;
import com.example.baget.calculation.preview.PreviewException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductAttachmentValidationTest {
    @Test void separateUnderframeComponentCannotBypassAntiframeRestriction() throws Exception {
        var request=new ObjectMapper().readValue("""
        {"items":[
          {"clientItemId":"sheets","kind":"PRODUCT","product":{"productType":"UNFRAMED_ARTWORK","widthMm":300,"heightMm":400,"productQuantity":1,"antiFrame":true}},
          {"clientItemId":"stretch","kind":"MOUNTING","mounting":{"mountingType":"UNDERFRAME","widthMm":300,"heightMm":400,"productQuantity":1,"underframePartNo":28060}}
        ]}
        """,EstimatePreviewRequest.class);
        assertThrows(PreviewException.class,()->ProductAttachmentValidation.validate(request.items()));
        assertDoesNotThrow(()->ProductAttachmentValidation.validate(request.items().subList(0,1)));
    }
}
