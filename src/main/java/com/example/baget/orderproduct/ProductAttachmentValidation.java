package com.example.baget.orderproduct;

import com.example.baget.calculation.estimate.EstimatePreviewRequest;
import com.example.baget.calculation.mounting.MountingPreviewRequest;
import com.example.baget.calculation.preview.PreviewException;
import org.springframework.http.HttpStatus;
import java.util.List;

/** Checks components belonging to one physical product, never unrelated estimate items. */
final class ProductAttachmentValidation {
    private ProductAttachmentValidation() {}
    static void validate(List<EstimatePreviewRequest.Item> components) {
        boolean antiFrame = components.stream().anyMatch(c -> c.product()!=null && Boolean.TRUE.equals(c.product().antiFrame()));
        boolean frame = components.stream().anyMatch(c -> c.product()!=null && (c.product().framePartNo()!=null || c.product().underframePartNo()!=null)
                || c.mounting()!=null && c.mounting().mountingType()==MountingPreviewRequest.MountingType.UNDERFRAME);
        if(antiFrame && frame) throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_ANTIFRAME","Антирама не може містити раму або підрамник");
    }
}
