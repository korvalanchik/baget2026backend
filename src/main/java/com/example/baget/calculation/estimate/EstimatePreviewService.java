package com.example.baget.calculation.estimate;

import com.example.baget.calculation.mounting.MountingPreviewService;
import com.example.baget.calculation.preview.PartPriceResolver;
import com.example.baget.calculation.preview.PreviewException;
import com.example.baget.calculation.product.ProductPreviewService;
import jakarta.validation.Validator;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.HashSet;

@Service
public class EstimatePreviewService {
    private final ProductPreviewService products;
    private final MountingPreviewService mountings;
    private final PartPriceResolver prices;
    private final Validator validator;

    public EstimatePreviewService(ProductPreviewService products, MountingPreviewService mountings,
                                  PartPriceResolver prices, Validator validator) {
        this.products = products;
        this.mountings = mountings;
        this.prices = prices;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public EstimatePreviewResponse preview(EstimatePreviewRequest request, Authentication auth) {
        prices.requireAuthenticated(auth);
        if (request == null || !validator.validate(request).isEmpty())
            throw badRequest("INVALID_REQUEST", "Перевірте список виробів та вкладені поля");

        // Validate the complete envelope before calling any calculator.
        var ids = new HashSet<String>();
        for (var item : request.items()) {
            if (!ids.add(item.clientItemId()))
                throw new EstimateItemException(item.clientItemId(), badRequest("DUPLICATE_CLIENT_ITEM_ID",
                        "clientItemId має бути унікальним у межах запиту"));
            boolean valid = switch (item.kind()) {
                case PRODUCT -> item.product() != null && item.mounting() == null;
                case MOUNTING -> item.mounting() != null && item.product() == null;
            };
            if (!valid) throw new EstimateItemException(item.clientItemId(), badRequest("INVALID_ITEM_SELECTION",
                    "Передайте лише product для PRODUCT або лише mounting для MOUNTING"));
        }

        var results = new ArrayList<EstimatePreviewResponse.Item>();
        for (var item : request.items()) {
            try {
                switch (item.kind()) {
                    case PRODUCT -> {
                        var result = products.preview(item.product(), auth);
                        results.add(new EstimatePreviewResponse.Item(item.clientItemId(), item.kind(),
                                result.total(), result, null));
                    }
                    case MOUNTING -> {
                        var result = mountings.preview(item.mounting(), auth);
                        results.add(new EstimatePreviewResponse.Item(item.clientItemId(), item.kind(),
                                result.total(), null, result));
                    }
                }
            } catch (PreviewException ex) {
                // No successful partial estimate is returned if any item cannot be priced.
                throw new EstimateItemException(item.clientItemId(), ex);
            }
        }
        return new EstimatePreviewResponse(results,
                EstimateTotals.sum(results.stream().map(EstimatePreviewResponse.Item::total).toList()), "UAH");
    }

    private static PreviewException badRequest(String code, String message) {
        return new PreviewException(HttpStatus.BAD_REQUEST, code, message);
    }
}
