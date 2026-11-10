package com.dentalclinic.appointment.repository;

import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Lightweight projection used by the scheduling engine to resolve occupied
 * cases without loading the full appointment aggregate.
 */
public interface AppointmentQueueOccupancy {

    default UUID getAppointmentId() {
        return null;
    }

    LocalDate getAppointmentDate();

    ScheduleSession getSession();

    Short getQueueNumber();
}
