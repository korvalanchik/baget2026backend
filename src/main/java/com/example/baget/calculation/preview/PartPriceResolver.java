package com.example.baget.calculation.preview;

import com.example.baget.parts.Parts;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class PartPriceResolver {
    public void requireAuthenticated(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new PreviewException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Потрібна авторизація");
        }
    }

    public BigDecimal resolve(Parts part, Authentication auth) {
        requireAuthenticated(auth);
        Set<String> roles = auth.getAuthorities().stream()
                .map(a -> a.getAuthority()).collect(Collectors.toSet());
        Double value;
        if (roles.contains("ROLE_ADMIN")) {
            value = part.getCost();
        } else if (roles.contains("ROLE_COUNTER")) {
            value = part.getListPrice_1();
        } else if (roles.contains("ROLE_LEVEL2")) {
            value = part.getListPrice();
        } else {
            // Same default as PartsService.resolvePriceColumn().
            value = part.getListPrice_3();
        }
        if (value == null || !Double.isFinite(value) || value < 0) {
            throw new PreviewException(HttpStatus.UNPROCESSABLE_ENTITY, "PRICE_NOT_CONFIGURED",
                    "Для матеріалу не задана коректна ціна обраного рівня");
        }
        // Zero is a valid explicit price; null must never become free material.
        return BigDecimal.valueOf(value);
    }
}
