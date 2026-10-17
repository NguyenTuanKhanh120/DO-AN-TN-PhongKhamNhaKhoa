package com.dentalclinic.schedule.dto;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.util.List;

public enum DoctorDayOffPeriod {
    MORNING,
    AFTERNOON,
    FULL_DAY;

    public List<ScheduleSession> sessions() {
        return switch (this) {
            case MORNING -> List.of(ScheduleSession.MORNING);
            case AFTERNOON -> List.of(ScheduleSession.AFTERNOON);
            case FULL_DAY -> List.of(ScheduleSession.MORNING, ScheduleSession.AFTERNOON);
        };
    }
}
