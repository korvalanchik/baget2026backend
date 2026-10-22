package com.example.baget.prro.config;

import com.example.baget.prro.client.DpsPrroGrpcClient;
import com.example.baget.prro.service.PrroSubmissionService;
import com.example.baget.prro.service.PrroUnknownResultInspectionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class PrroDisabledStartupTest {

    @Test
    void transportAndDependentServicesAreAbsentWhenPrroIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        PrroGrpcConfiguration.class,
                        DpsPrroGrpcClient.class,
                        PrroSubmissionService.class,
                        PrroUnknownResultInspectionService.class)
                .withPropertyValues("baget.prro.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(DpsPrroGrpcClient.class);
                    assertThat(context).doesNotHaveBean(PrroSubmissionService.class);
                    assertThat(context).doesNotHaveBean(PrroUnknownResultInspectionService.class);
                });
    }
}
