package com.example.baget.calculation.product;

import com.example.baget.calculation.preview.*;
import com.example.baget.calculation.suspension.*;
import com.example.baget.parts.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;

/** Internal composer called inside ProductPreviewService's read-only transaction. */
@Component
public class MirrorAccessoriesComposer {
    private final PartsRepository parts;
    private final MaterialPreviewService materials;
    private final SuspensionPreviewService suspensions;

    public MirrorAccessoriesComposer(PartsRepository parts, MaterialPreviewService materials,
                                    SuspensionPreviewService suspensions) {
        this.parts = parts; this.materials = materials; this.suspensions = suspensions;
    }

    public List<ProductPreviewResponse.Line> compose(ProductPreviewRequest r, Authentication auth) {
        var lines = new ArrayList<ProductPreviewResponse.Line>();
        for (var backing : SheetComposition.backings(r)) {
            if (!SuspensionRules.isRigidBacking(backing.partNo()))
                throw new PreviewException(HttpStatus.BAD_REQUEST, "UNSUPPORTED_BACKING_PART", "Підтримуються ДВП 4460 та ПВХ 27520");
            lines.add(SheetComposition.sheet("BACKING", backing.partNo(), backing.quantityPerProduct(), r, parts, materials, auth));
        }
        if (r.suspension() != null) {
            var choice = r.suspension();
            // Never accept another width, quantity or hasMirror/hasDvp from nested suspension JSON.
            var result = suspensions.preview(new SuspensionPreviewRequest(SheetComposition.width(r), r.productQuantity(),
                    SheetComposition.rigidBackingPartNo(r), SheetComposition.mirrorPartNo(r), choice.hangerMode(), choice.hangerPartNo(),
                    choice.hangersPerProduct(), choice.includePozzi(), choice.pozziPerProduct(), choice.includeCord()), auth);
            lines.addAll(result.lines());
        }
        return List.copyOf(lines);
    }
}
