package com.example.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@SpringBootApplication
public class SchedulerApplication {

    static void main(String[] args) {
        SpringApplication.run(SchedulerApplication.class, args);
    }

    @Bean
    MethodToolCallbackProvider methodToolCallbackProvider(DogAdoptionScheduler scheduler) {
        return MethodToolCallbackProvider
                .builder()
                .toolObjects(scheduler)
                .build();
    }
}

@Component
@Slf4j
class DogAdoptionScheduler {

    @Tool(description = "schedule an appointment to pickup or adopt a " +
            "dog from a Pooch Palace location")
    String schedule(int dogId, String dogName) {
        String cheduledDay = Instant
                .now()
                .plus(3, ChronoUnit.DAYS)
                .toString();
        log.info("Scheduling adoption for dog {} on {}!", dogName, cheduledDay);
        return cheduledDay;
    }
}