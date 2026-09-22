package com.example.baget.calculation.product;

import com.example.baget.calculation.preview.PreviewException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class MirrorFrameRule {
    public record ComponentLine(Long partNo, String role, String source) {}
    private final long mirrorPartNo;
    private final long gluePartNo;

    public MirrorFrameRule(
            @Value("${baget.calculation.mirror-part-no:27760}") long mirrorPartNo,
            @Value("${baget.calculation.mirror-glue-part-no:28540}") long gluePartNo) {
        if (mirrorPartNo <= 0 || gluePartNo <= 0 || mirrorPartNo == gluePartNo) {
            throw new IllegalArgumentException("Invalid mirror/glue configuration");
        }
        this.mirrorPartNo = mirrorPartNo;
        this.gluePartNo = gluePartNo;
    }

    public List<ComponentLine> compose(ProductPreviewRequest request) {
        if (request.productType() != ProductPreviewRequest.ProductType.MIRROR_IN_FRAME) {
            throw invalid("Тип виробу не підтримується");
        }
        // AREA alone does not mean MIRROR: glass/canvas must not trigger mirror rules.
        if (request.mirrorPartNo() == null || request.mirrorPartNo() != mirrorPartNo) {
            throw invalid("Ця позиція не зареєстрована як дзеркало в правилі комплектації");
        }
        if (request.framePartNo() == null || request.framePartNo() == mirrorPartNo
                || request.framePartNo() == gluePartNo) {
            throw invalid("Некоректна позиція рами");
        }
        return List.of(
                new ComponentLine(request.framePartNo(), "FRAME", "SELECTED"),
                new ComponentLine(request.mirrorPartNo(), "MIRROR", "SELECTED"),
                glueComponent()
        );
    }

    public ComponentLine glueComponent() {
        return new ComponentLine(gluePartNo, "MIRROR_GLUE", "MIRROR_IN_FRAME_RULE");
    }

    private static PreviewException invalid(String message) {
        return new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_COMPOSITION", message);
    }
}
