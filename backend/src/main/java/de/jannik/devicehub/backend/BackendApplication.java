package de.jannik.devicehub.backend;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendApplication.class, args);
    }

    /** Eigene Bean, damit Tests eine feste oder verstellbare Uhr einsetzen können. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
