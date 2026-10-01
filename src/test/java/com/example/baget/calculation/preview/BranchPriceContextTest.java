package com.example.baget.calculation.preview;
import com.example.baget.parts.Parts;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class BranchPriceContextTest {
 @Test void branchTierOverridesOperatorRole(){var prices=new PartPriceResolver();var p=new Parts();p.setCost(10.0);p.setListPrice(100.0);p.setListPrice_1(70.0);p.setListPrice_3(120.0);var auth=new UsernamePasswordAuthenticationToken("admin","",List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));assertEquals(0,new BigDecimal("100").compareTo(prices.resolve(p,prices.forTier(auth,"OWN"))));assertEquals(0,new BigDecimal("120").compareTo(prices.resolve(p,prices.forTier(auth,"RESELLER"))));}
 @Test void roleTextCannotForgeInternalTier(){var prices=new PartPriceResolver();var p=new Parts();p.setListPrice_3(120.0);p.setListPrice(100.0);var auth=new UsernamePasswordAuthenticationToken("x","",List.of(new SimpleGrantedAuthority("OWN")));assertEquals(0,new BigDecimal("120").compareTo(prices.resolve(p,auth)));}
}
