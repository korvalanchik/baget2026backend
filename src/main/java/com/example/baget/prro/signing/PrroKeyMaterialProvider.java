package com.example.baget.prro.signing;

import com.google.cloud.secretmanager.v1.AccessSecretVersionResponse;
import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretVersionName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads the cashier's PKCS#12 key and password from Google Secret Manager.
 *
 * <p>Secret payload for the PFX secret must contain the raw binary .pfx file,
 * not its Base64 text representation. This class never logs or exposes secret
 * contents through a web endpoint.</p>
 */
@Component
@ConditionalOnProperty(prefix = "baget.prro.signing", name = "secret-manager-enabled",
        havingValue = "true")
public class PrroKeyMaterialProvider {

    private final SecretManagerServiceClient secretManager;
    private final String projectId;
    private final String pfxSecretId;
    private final String passwordSecretId;

    public PrroKeyMaterialProvider(
            SecretManagerServiceClient secretManager,
            @Value("${GOOGLE_CLOUD_PROJECT:}") String projectId,
            @Value("${baget.prro.signing.pfx-secret-id:baget-prro-kep-pfx}") String pfxSecretId,
            @Value("${baget.prro.signing.password-secret-id:baget-prro-kep-password}") String passwordSecretId) {
        this.secretManager = Objects.requireNonNull(secretManager, "secretManager");
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException("GOOGLE_CLOUD_PROJECT must be set when PRRO Secret Manager is enabled");
        }
        this.projectId = projectId.trim();
        this.pfxSecretId = requireSecretId(pfxSecretId, "PFX");
        this.passwordSecretId = requireSecretId(passwordSecretId, "password");
    }

    public KeyMaterial load() {
        byte[] pfx = access(pfxSecretId);
        String password = new String(access(passwordSecretId), StandardCharsets.UTF_8);
        if (pfx.length == 0) {
            throw new IllegalStateException("PRRO PFX secret is empty");
        }
        if (password.isEmpty()) {
            throw new IllegalStateException("PRRO PFX password secret is empty");
        }
        return new KeyMaterial(pfx, password);
    }

    private byte[] access(String secretId) {
        SecretVersionName version = SecretVersionName.of(projectId, secretId, "latest");
        AccessSecretVersionResponse response = secretManager.accessSecretVersion(version);
        return response.getPayload().getData().toByteArray();
    }

    private static String requireSecretId(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PRRO " + label + " secret ID must not be blank");
        }
        return value.trim();
    }

    public record KeyMaterial(byte[] pfxBytes, String password) {
        public KeyMaterial {
            if (pfxBytes == null || pfxBytes.length == 0) {
                throw new IllegalArgumentException("PFX bytes must not be empty");
            }
            pfxBytes = pfxBytes.clone();
            Objects.requireNonNull(password, "password");
        }

        @Override
        public byte[] pfxBytes() {
            return pfxBytes.clone();
        }
    }
}
