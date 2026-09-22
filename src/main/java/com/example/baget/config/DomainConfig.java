package com.example.baget.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;


@Configuration
@EntityScan("com.example.baget")
@EnableJpaRepositories("com.example.baget")
@EnableTransactionManagement
public class DomainConfig {
}
