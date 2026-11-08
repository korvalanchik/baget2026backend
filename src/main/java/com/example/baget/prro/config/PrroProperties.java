package com.example.baget.prro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Locale;

/**
 * One deployment-wide DPS endpoint. Individual PRRO devices do not carry
 * their own TEST/PRODUCTION mode.
 */
@ConfigurationProperties(prefix = "baget.prro")
public class PrroProperties {
    private static final String TEST_HOST = "cabinet.tax.gov.ua";
    private static final int TEST_PORT = 9443;
    private static final String PRODUCTION_HOST = "prro.tax.gov.ua";
    private static final String PRODUCTION_FALLBACK_HOST = "prro2.tax.gov.ua";
    private static final int PRODUCTION_PORT = 443;

    /** Safe default: DPS developer endpoint. */
    private String host = TEST_HOST;
    private int port = TEST_PORT;
    private boolean tlsEnabled = true;
    private boolean enabled = false;
    private String taxCode = "Н";
    private long rpcDeadlineMillis = 15_000;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public boolean isTlsEnabled() { return tlsEnabled; }
    public void setTlsEnabled(boolean tlsEnabled) { this.tlsEnabled = tlsEnabled; }
    public long getRpcDeadlineMillis() { return rpcDeadlineMillis; }
    public void setRpcDeadlineMillis(long rpcDeadlineMillis) { this.rpcDeadlineMillis = rpcDeadlineMillis; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTaxCode() { return taxCode; }

    public void setTaxCode(String taxCode) {
        if (taxCode == null || taxCode.isBlank()
                || taxCode.codePointCount(0, taxCode.length()) != 1) {
            throw new IllegalArgumentException("PRRO tax code must contain exactly one character");
        }
        this.taxCode = taxCode;
    }

    public String getEndpointMode() {
        String normalizedHost = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        if (TEST_HOST.equals(normalizedHost) && port == TEST_PORT && tlsEnabled) return "TEST";
        if ((PRODUCTION_HOST.equals(normalizedHost)
                || PRODUCTION_FALLBACK_HOST.equals(normalizedHost))
                && port == PRODUCTION_PORT && tlsEnabled) return "PRODUCTION";
        return "UNSUPPORTED";
    }

    public boolean isTestEndpoint() {
        return "TEST".equals(getEndpointMode());
    }

    /** Fail closed on unsupported host/port/TLS combinations. */
    public void validateEndpoint() {
        if ("UNSUPPORTED".equals(getEndpointMode())) {
            throw new IllegalStateException(
                    "Unsupported DPS PRRO endpoint. Use cabinet.tax.gov.ua:9443 (TEST) "
                            + "or prro.tax.gov.ua:443 / prro2.tax.gov.ua:443 (PRODUCTION), with TLS enabled.");
        }
    }
}
