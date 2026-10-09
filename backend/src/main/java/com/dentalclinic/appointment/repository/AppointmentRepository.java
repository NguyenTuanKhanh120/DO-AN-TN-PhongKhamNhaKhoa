package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    @Query("""
            select appointment.scheduledStartAt
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.scheduledStartAt >= :rangeStart
              and appointment.scheduledStartAt < :rangeEnd
              and appointment.status <> :cancelledStatus
            """)
    List<Instant> findOccupiedStartTimes(
            @Param("doctorId") UUID doctorId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("cancelledStatus") AppointmentStatus cancelledStatus
    );

    @Query("""
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.scheduledStartAt = :scheduledStartAt
              and appointment.status <> :cancelledStatus
            """)
    boolean existsOccupiedStart(
            @Param("doctorId") UUID doctorId,
            @Param("scheduledStartAt") Instant scheduledStartAt,
            @Param("cancelledStatus") AppointmentStatus cancelledStatus
    );
}
