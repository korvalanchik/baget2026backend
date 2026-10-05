package com.example.baget.calculation.product;

import com.example.baget.calculation.preview.*;
import com.example.baget.parts.*;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;

@Service
public class ProductPreviewService {
    private final MirrorFrameRule rule;
    private final PartsRepository parts;
    private final MaterialPreviewService materials;
    private final PartPriceResolver prices;
    private final Validator validator;
    private final UnderframePreviewComposer underframes;
    private final MirrorAccessoriesComposer accessories;

    public ProductPreviewService(MirrorFrameRule rule, PartsRepository parts,
            MaterialPreviewService materials, PartPriceResolver prices, Validator validator,
            UnderframePreviewComposer underframes, MirrorAccessoriesComposer accessories) {
        this.rule = rule;
        this.parts = parts;
        this.materials = materials;
        this.prices = prices;
        this.validator = validator;
        this.underframes = underframes;
        this.accessories = accessories;
    }

    @Transactional(readOnly = true)
    public ProductPreviewResponse preview(ProductPreviewRequest request, Authentication auth) {
        prices.requireAuthenticated(auth);
        if (request == null || !validator.validate(request).isEmpty()) {
            throw new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Перевірте параметри виробу");
        }
        validateSelection(request);
        SheetComposition.validate(request);
        if(Boolean.TRUE.equals(request.antiFrame()) && (request.productType()!=ProductPreviewRequest.ProductType.UNFRAMED_ARTWORK
                || request.framePartNo()!=null || request.underframePartNo()!=null || SheetComposition.glass(request).isEmpty()
                || SheetComposition.backings(request).stream().noneMatch(x->x.partNo()==4460L)))
            throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_ANTIFRAME","Антирама потребує скла/дзеркала та ДВП 4460, без рами й підрамника");
        if(Boolean.TRUE.equals(request.includeUnderframeFasteners()) && (request.productType()!=ProductPreviewRequest.ProductType.FRAMED_ARTWORK
                || request.underframePartNo()==null || !java.util.Set.of(28060L,28020L,28210L,29720L).contains(request.underframePartNo())))
            throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_UNDERFRAME_FASTENERS","Кріплення потребує багета та вибраного підрамника");
        if (request.productType() == ProductPreviewRequest.ProductType.UNDERFRAME) {
            return summarize(request, underframes.compose(request, auth));
        }
        if (request.productType() == ProductPreviewRequest.ProductType.FRAMED_ARTWORK) {
            var lines = SheetComposition.compose(request, parts, materials, rule, auth);
            if (!SheetComposition.backings(request).isEmpty() || request.suspension() != null)
                lines.addAll(accessories.compose(request, auth));
            SheetComposition.addFasteners(request,lines,parts,materials,auth);
            return summarize(request, lines);
        }
        if (request.productType() == ProductPreviewRequest.ProductType.UNFRAMED_ARTWORK) {
            var lines=new ArrayList<>(SheetComposition.composeUnframed(request, parts, materials, auth));
            if(request.suspension()!=null) {
                var choice=request.suspension();
                if(SheetComposition.backings(request).isEmpty() || choice.includePozzi() || Boolean.TRUE.equals(choice.includeCord())
                    || (choice.hangerMode()==com.example.baget.calculation.suspension.SuspensionRules.Mode.MANUAL
                        && (choice.hangerPartNo()==null || !java.util.Set.of(27580L,14450L).contains(choice.hangerPartNo()))))
                    throw new PreviewException(HttpStatus.BAD_REQUEST,"INVALID_UNFRAMED_HANGER","Без рами дозволений крокодил на заднику, без шнурка й лапок");
                // Sheets already include the backing; only append the hanger from accessories.
                accessories.compose(request,auth).stream().filter(l->!l.role().equals("BACKING")).forEach(lines::add);
            }
            SheetComposition.addFasteners(request,lines,parts,materials,auth);
            return summarize(request,lines);
        }
        var components = rule.compose(request);
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        for (var component : components) {
            var part = parts.findById(component.partNo()).orElseThrow(() ->
                    new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "COMPOSITION_PART_MISSING",
                            "Не знайдено матеріал " + component.partNo() + " для " + component.role()));
            var expected = switch (component.role()) {
                case "FRAME" -> CalculationMethod.FRAME_PROFILE;
                case "MIRROR" -> CalculationMethod.AREA;
                case "MIRROR_GLUE" -> CalculationMethod.PERIMETER;
                default -> throw new IllegalStateException("Unknown component role");
            };
            if (part.getPartKind() != PartKind.MATERIAL || part.getCalculationMethod() != expected) {
                throw new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_COMPONENT_SETTINGS",
                        "Позиція " + component.partNo() + " не налаштована для " + component.role());
            }
            // Reuse tested validation, consumption and role-based pricing from step 3.
            var result = materials.preview(new MaterialPreviewRequest(component.partNo(),
                    request.widthMm(), request.heightMm(), request.productQuantity(), 1), auth);
            lines.add(new ProductPreviewResponse.Line(component.role(), component.source(), result));
        }
        if (!SheetComposition.backings(request).isEmpty() || request.suspension() != null)
            lines.addAll(accessories.compose(request, auth));
        return summarize(request, lines);
    }

    private static void validateSelection(ProductPreviewRequest r) {
        if (Long.valueOf(33190L).equals(r.framePartNo()))
            throw new PreviewException(HttpStatus.BAD_REQUEST, "LEGACY_NO_FRAME_PART",
                    "Позиція «Без рами» не є багетом. Оберіть виріб без рами (UNFRAMED_ARTWORK).");
        boolean valid = switch (r.productType()) {
            case MIRROR_IN_FRAME -> r.framePartNo() != null && r.mirrorPartNo() != null && r.underframePartNo() == null
                    && r.glassPartNo() == null && r.glassLayers() == null && r.matPartNo() == null
                    && r.matLayers() == null && r.matMargins() == null && r.glass() == null && r.mats() == null;
            case FRAMED_ARTWORK -> r.framePartNo() != null && r.mirrorPartNo() == null && (r.underframePartNo() == null || Boolean.TRUE.equals(r.includeUnderframeFasteners()));
            case UNFRAMED_ARTWORK -> r.framePartNo() == null && r.mirrorPartNo() == null
                    && r.underframePartNo() == null
                    && (!SheetComposition.glass(r).isEmpty() || !SheetComposition.backings(r).isEmpty() || !SheetComposition.mats(r).isEmpty());
            case UNDERFRAME -> r.underframePartNo() != null && r.framePartNo() == null && r.mirrorPartNo() == null
                    && r.backingPartNo() == null && r.backingLayers() == null
                    && r.glassPartNo() == null && r.glassLayers() == null && r.matPartNo() == null
                    && r.matLayers() == null && r.matMargins() == null
                    && r.glass() == null && r.backings() == null && r.mats() == null;
        };
        if (!valid) throw new PreviewException(HttpStatus.BAD_REQUEST, "INVALID_PRODUCT_SELECTION",
                "UNFRAMED_ARTWORK: листові матеріали без рами, опційно підвіс для задника; MIRROR_IN_FRAME: рама й дзеркало, опційно задник і підвіс; FRAMED_ARTWORK: рама, опційно скло, паспарту, задник і підвіс; UNDERFRAME: підрамник, опційно підвіси та шнурок");
    }

    private static ProductPreviewResponse summarize(ProductPreviewRequest request,
            java.util.List<ProductPreviewResponse.Line> lines) {
        var raw = lines.stream().map(line -> line.calculation().totalConsumption()
                .multiply(line.calculation().unitPrice())).toList();
        var totals = ProductTotals.fromUnroundedAmounts(raw);
        return new ProductPreviewResponse(request.productType(), request.productQuantity(),
                java.util.List.copyOf(lines), totals.linesSubtotal(), totals.roundingAdjustment(), totals.total(), "UAH");
    }
}
