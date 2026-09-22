package com.example.baget;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@SpringBootApplication
@EnableCaching
public class BagetApplication {

    public static void main(final String[] args) {
        SpringApplication.run(BagetApplication.class, args);
    }

}
