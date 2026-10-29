package com.example.baget.prro.signing;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import com.google.cloud.secretmanager.v1.SecretManagerServiceSettings;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
@ConditionalOnProperty(prefix = "baget.prro.signing", name = "secret-manager-enabled",
        havingValue = "true")
public class PrroSecretManagerConfiguration {

    @Bean(destroyMethod = "close")
    public SecretManagerServiceClient prroSecretManagerClient() throws IOException {
        return SecretManagerServiceClient.create(
                SecretManagerServiceSettings.newBuilder().build());
    }
}
