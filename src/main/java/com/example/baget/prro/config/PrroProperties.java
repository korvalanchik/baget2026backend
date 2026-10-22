package com.example.baget.prro.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "baget.prro")
public class PrroProperties {

    /**
     * Safe default: DPS developer endpoint. Test receipts are not fiscal.
     */
    private String host = "cabinet.tax.gov.ua";
    private int port = 9443;
    private boolean tlsEnabled = true;
    private boolean enabled = false;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
