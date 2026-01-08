package com.example.scheduler.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
@Slf4j
/**
 * Called from Adoptions application at server start.
 * If this service is not running and Adoptions is configured for an internal tool,
 * the application will use an internal scheduler.
 */
public class DogAdoptionScheduler {

    @Tool(description = "schedule an appointment to pickup or adopt a " +
            "dog from a Pooch Palace location")
    String schedule(int dogId, String dogName) {
        String cheduledDay = Instant
                .now()
                .plus(3, ChronoUnit.DAYS)
                .toString();
        log.info("Scheduling adoption for dog {} with id {} on {}!", dogName, dogId, cheduledDay);
        return cheduledDay;
    }

    @Tool(description = """
            unschedule an appointment to pickup or adopt a
            dog from a Pooch Palace location
            """)
    String unschedule(int dogId, String dogName, String scheduledDay) {
        log.info("Unscheduling adoption for dog {} with id {} on {}!", dogName, dogId, scheduledDay);
        return scheduledDay;
    }
}
