package com.dentalclinic.common.util;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Component
public class ClinicTimeProvider {

    private final Clock clock;

    public ClinicTimeProvider(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public ZonedDateTime nowAtClinic() {
        return ZonedDateTime.now(clock);
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    public ZoneId zoneId() {
        return clock.getZone();
    }

    public Instant toInstant(LocalDate date, LocalTime time) {
        return ZonedDateTime.of(date, time, clock.getZone()).toInstant();
    }

    public ZonedDateTime toClinicTime(Instant instant) {
        return instant.atZone(clock.getZone());
    }
}
