package com.example.library.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class AopConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}