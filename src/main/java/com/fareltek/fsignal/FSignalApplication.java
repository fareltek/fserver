package com.fareltek.fsignal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.reactive.ReactiveUserDetailsServiceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication(exclude = {ReactiveUserDetailsServiceAutoConfiguration.class})
public class FSignalApplication {

    public static void main(String[] args) {
        SpringApplication.run(FSignalApplication.class, args);
    }
}
