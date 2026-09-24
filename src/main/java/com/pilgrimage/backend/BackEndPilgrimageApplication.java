package com.pilgrimage.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackEndPilgrimageApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackEndPilgrimageApplication.class, args);
    }
}
