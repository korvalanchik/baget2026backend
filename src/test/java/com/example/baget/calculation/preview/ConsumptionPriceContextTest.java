package com.example.baget.calculation.preview;
import com.example.baget.parts.Parts;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ConsumptionPriceContextTest {
    @Test void internalQuantityContextDoesNotReadPriceOrChangeNormalPricing() {
        var resolver=new PartPriceResolver();var part=new Parts();
        var auth=new UsernamePasswordAuthenticationToken("master","",List.of(new SimpleGrantedAuthority("ROLE_FOREMAN")));
        assertEquals(BigDecimal.ZERO,resolver.resolve(part,resolver.consumptionOnly(auth)));
        assertThrows(PreviewException.class,()->resolver.resolve(part,auth));
        part.setListPrice_3(13.5);assertEquals(BigDecimal.valueOf(13.5),resolver.resolve(part,auth));
    }
    @Test void authorityNameCannotActivateQuantityMode() {
        var resolver=new PartPriceResolver();var part=new Parts();part.setListPrice_3(13.5);
        var auth=new UsernamePasswordAuthenticationToken("user","",List.of(new SimpleGrantedAuthority("QuantityAuthentication")));
        assertEquals(BigDecimal.valueOf(13.5),resolver.resolve(part,auth));
    }
    @Test void unauthenticatedCallerCannotCreateQuantityContext(){var resolver=new PartPriceResolver();assertThrows(PreviewException.class,()->resolver.consumptionOnly(new UsernamePasswordAuthenticationToken("user","")));}
}
