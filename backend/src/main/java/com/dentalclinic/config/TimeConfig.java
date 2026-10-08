package com.dentalclinic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public ZoneId clinicZoneId(@Value("${clinic.timezone:Asia/Ho_Chi_Minh}") String timezone) {
        return ZoneId.of(timezone);
    }

    @Bean
    public Clock clinicClock(ZoneId clinicZoneId) {
        return Clock.system(clinicZoneId);
    }
}
