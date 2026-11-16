package com.example.scheduler;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class SchedulerConfig {

    /** UTC-годинник; CatalogSnapshotService сам переводить його в зону Europe/Kyiv. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
