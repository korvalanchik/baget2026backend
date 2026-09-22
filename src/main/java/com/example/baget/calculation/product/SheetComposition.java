package com.example.baget.calculation.product;

import com.example.baget.calculation.preview.*;
import com.example.baget.parts.*;
import com.example.baget.calculation.suspension.SuspensionRules;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/** Sheet counts are per product. They never multiply frame or suspension quantities. */
public final class SheetComposition {
    private static final Set<Long> GLASS = Set.of(12670L, 27540L, 27510L, 27470L, SuspensionRules.MIRROR);
    private static final Set<Long> BACKINGS = Set.of(SuspensionRules.DVP, SuspensionRules.PVC);
    private static final Set<Long> MATS = Set.of(5160L, 28010L, 29500L);
    private SheetComposition() {}

    public static List<SheetSelection> glass(ProductPreviewRequest r) {
        return selected(r.glass(), r.glassPartNo(), r.glassLayers());
    }
    public static List<SheetSelection> backings(ProductPreviewRequest r) {
        return selected(r.backings(), r.backingPartNo(), r.backingLayers());
    }
    public static List<SheetSelection> mats(ProductPreviewRequest r) {
        return selected(r.mats(), r.matPartNo(), r.matLayers());
    }
    private static List<SheetSelection> selected(List<SheetSelection> group, Long partNo, Integer count) {
        if (group != null) return group;
        return partNo == null ? List.of() : List.of(new SheetSelection(partNo, count == null ? 1 : count));
    }
    public static Long mirrorPartNo(ProductPreviewRequest r) {
        return r.productType() == ProductPreviewRequest.ProductType.FRAMED_ARTWORK
                ? glass(r).stream().map(SheetSelection::partNo).filter(id -> id == SuspensionRules.MIRROR)
                    .findFirst().orElse(null)
                : r.mirrorPartNo();
    }
    public static Long rigidBackingPartNo(ProductPreviewRequest r) {
        return backings(r).stream().map(SheetSelection::partNo).filter(SuspensionRules::isRigidBacking)
                .findFirst().orElse(null);
    }

    public static BigDecimal width(ProductPreviewRequest r) {
        return r.matMargins() == null ? r.widthMm()
                : r.widthMm().add(r.matMargins().leftMm()).add(r.matMargins().rightMm());
    }
    public static BigDecimal height(ProductPreviewRequest r) {
        return r.matMargins() == null ? r.heightMm()
                : r.heightMm().add(r.matMargins().topMm()).add(r.matMargins().bottomMm());
    }
    public static void validate(ProductPreviewRequest r) {
        validateGroup(r.glass(), r.glassPartNo(), r.glassLayers(), glass(r), GLASS, "UNSUPPORTED_SHEET_PART");
        validateGroup(r.backings(), r.backingPartNo(), r.backingLayers(), backings(r), BACKINGS, "UNSUPPORTED_BACKING_PART");
        validateGroup(r.mats(), r.matPartNo(), r.matLayers(), mats(r), MATS, "UNSUPPORTED_SHEET_PART");
        if ((r.glassLayers() != null && r.glassPartNo() == null)
                || (r.backingLayers() != null && r.backingPartNo() == null)
                || (r.matLayers() != null && r.matPartNo() == null)
                || (r.matMargins() != null && mats(r).isEmpty())
                || (!mats(r).isEmpty() && r.matMargins() == null))
            throw bad("INVALID_LAYER_SELECTION", "Шари потребують матеріалу; для паспарту задайте чотири поля matMargins");
        if (width(r).compareTo(BigDecimal.valueOf(100000)) > 0
                || height(r).compareTo(BigDecimal.valueOf(100000)) > 0)
            throw bad("INVALID_OUTER_DIMENSIONS", "Розмір із полями паспарту перевищує 100000 мм");
    }

    private static void validateGroup(List<SheetSelection> group, Long legacyPart, Integer legacyCount,
            List<SheetSelection> selections, Set<Long> allowed, String unsupportedCode) {
        if (group != null && (legacyPart != null || legacyCount != null))
            throw bad("AMBIGUOUS_SHEET_SELECTION", "Для однієї групи передайте список або одиночний матеріал, не обидва");
        var ids = new HashSet<Long>();
        for (var selection : selections) {
            if (!allowed.contains(selection.partNo()))
                throw bad(unsupportedCode, "Матеріал " + selection.partNo() + " не входить до обраної групи");
            if (!ids.add(selection.partNo()))
                throw bad("DUPLICATE_SHEET_PART", "Об'єднайте кількість шарів одного матеріалу в quantityPerProduct");
        }
    }

    public static List<ProductPreviewResponse.Line> compose(ProductPreviewRequest r,
            PartsRepository parts, MaterialPreviewService materials, MirrorFrameRule mirrorRule, Authentication auth) {
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        var frame = load(parts, r.framePartNo());
        if (frame.getPartKind() != PartKind.MATERIAL || frame.getCalculationMethod() != CalculationMethod.FRAME_PROFILE)
            throw settings("FRAME");
        lines.add(new ProductPreviewResponse.Line("FRAME", "SELECTED", materials.preview(
                new MaterialPreviewRequest(r.framePartNo(), width(r), height(r), r.productQuantity(), 1), auth)));
        for (var selection : glass(r))
            lines.add(sheet(selection.partNo() == SuspensionRules.MIRROR ? "MIRROR" : "GLASS",
                    selection.partNo(), selection.quantityPerProduct(), r, parts, materials, auth));
        if (mirrorPartNo(r) != null) {
            var glue = mirrorRule.glueComponent();
            var part = load(parts, glue.partNo());
            if (part.getPartKind() != PartKind.MATERIAL || part.getCalculationMethod() != CalculationMethod.PERIMETER
                    || part.getUnitType() != UnitType.METER || part.getCalculationParams() == null
                    || !part.getCalculationParams().isEmpty())
                throw settings("MIRROR_GLUE");
            // One perimeter per product, independent of mirror layers and other glass selections.
            lines.add(new ProductPreviewResponse.Line(glue.role(), glue.source(), materials.preview(
                    new MaterialPreviewRequest(glue.partNo(), width(r), height(r), r.productQuantity(), 1), auth)));
        }
        for (var selection : mats(r))
            lines.add(sheet("MAT", selection.partNo(), selection.quantityPerProduct(), r, parts, materials, auth));
        return lines;
    }

    public static ProductPreviewResponse.Line sheet(String role, long partNo, Integer layers,
            ProductPreviewRequest r, PartsRepository parts, MaterialPreviewService materials, Authentication auth) {
        var part = load(parts, partNo);
        if (part.getPartKind() != PartKind.MATERIAL || part.getUnitType() != UnitType.SQUARE_METER
                || part.getCalculationMethod() != CalculationMethod.AREA
                || part.getCalculationParams() == null || !part.getCalculationParams().isEmpty())
            throw settings(role);
        var single = materials.preview(new MaterialPreviewRequest(partNo, width(r), height(r),
                r.productQuantity(), 1), auth);
        var count = BigDecimal.valueOf(layers == null ? 1 : layers);
        var perProduct = single.consumptionPerProduct().multiply(count);
        var total = single.totalConsumption().multiply(count);
        var calculation = new MaterialPreviewResponse(single.partNo(), single.description(), single.calculationMethod(),
                single.unit(), perProduct, single.productQuantity(), total, single.unitPrice(),
                total.multiply(single.unitPrice()).setScale(2, RoundingMode.HALF_UP), single.currency());
        return new ProductPreviewResponse.Line(role, "SELECTED", calculation);
    }

    private static Parts load(PartsRepository parts, long id) {
        return parts.findById(id).orElseThrow(() -> new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY,
                "COMPOSITION_PART_MISSING", "Не знайдено матеріал " + id));
    }
    private static PreviewException settings(String role) {
        return new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY,
                role.equals("BACKING") ? "INVALID_BACKING_SETTINGS" : "INVALID_COMPONENT_SETTINGS",
                "Перевірте налаштування матеріалу для " + role);
    }
    private static PreviewException bad(String code, String message) {
        return new PreviewException(HttpStatus.BAD_REQUEST, code, message);
    }
}
