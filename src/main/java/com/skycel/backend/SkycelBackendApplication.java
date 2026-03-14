package com.skycel.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class SkycelBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkycelBackendApplication.class, args);
    }

}
