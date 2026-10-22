package com.example.baget.prro.config;

import com.programika.rro.ws.chk.ChkIncomeServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PrroProperties.class)
@ConditionalOnProperty(prefix = "baget.prro", name = "enabled", havingValue = "true")
public class PrroGrpcConfiguration {

    @Bean(destroyMethod = "shutdown")
    public ManagedChannel dpsPrroChannel(PrroProperties properties) {
        ManagedChannelBuilder<?> builder = ManagedChannelBuilder
                .forAddress(properties.getHost(), properties.getPort());

        if (properties.isTlsEnabled()) {
            builder.useTransportSecurity();
        } else {
            builder.usePlaintext();
        }

        return builder.build();
    }

    @Bean
    public ChkIncomeServiceGrpc.ChkIncomeServiceBlockingStub dpsPrroBlockingStub(
            ManagedChannel dpsPrroChannel) {
        return ChkIncomeServiceGrpc.newBlockingStub(dpsPrroChannel);
    }
}
