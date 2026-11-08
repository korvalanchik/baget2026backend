package com.example.baget.prro.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PrroPropertiesTest {

    @Test
    void defaultsToDpsDeveloperEndpoint() {
        PrroProperties properties = new PrroProperties();

        assertEquals("TEST", properties.getEndpointMode());
        assertTrue(properties.isTestEndpoint());
        assertDoesNotThrow(properties::validateEndpoint);
    }

    @Test
    void recognizesProductionPrimaryAndFallbackEndpoints() {
        PrroProperties properties = new PrroProperties();

        properties.setHost("prro.tax.gov.ua");
        properties.setPort(443);
        assertEquals("PRODUCTION", properties.getEndpointMode());
        assertFalse(properties.isTestEndpoint());
        assertDoesNotThrow(properties::validateEndpoint);

        properties.setHost("prro2.tax.gov.ua");
        assertEquals("PRODUCTION", properties.getEndpointMode());
        assertDoesNotThrow(properties::validateEndpoint);
    }

    @Test
    void rejectsUnsupportedEndpointAndPlaintextTransport() {
        PrroProperties properties = new PrroProperties();

        properties.setHost("example.com");
        properties.setPort(443);
        assertEquals("UNSUPPORTED", properties.getEndpointMode());
        assertThrows(IllegalStateException.class, properties::validateEndpoint);

        properties.setHost("cabinet.tax.gov.ua");
        properties.setPort(9443);
        properties.setTlsEnabled(false);
        assertEquals("UNSUPPORTED", properties.getEndpointMode());
        assertThrows(IllegalStateException.class, properties::validateEndpoint);
    }

    @Test
    void acceptsOnlyOneCharacterTaxCode() {
        PrroProperties properties = new PrroProperties();

        properties.setTaxCode("Н");
        assertEquals("Н", properties.getTaxCode());
        assertThrows(IllegalArgumentException.class, () -> properties.setTaxCode("VAT"));
        assertThrows(IllegalArgumentException.class, () -> properties.setTaxCode(" "));
    }
}
