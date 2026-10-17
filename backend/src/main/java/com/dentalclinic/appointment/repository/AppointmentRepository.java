package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID>,
        JpaSpecificationExecutor<Appointment> {

    @Override
    @EntityGraph(attributePaths = {
            "patient.user",
            "doctorService.doctor.user",
            "doctorService.service",
            "previousAppointment"
    })
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    @EntityGraph(attributePaths = {
            "patient.user",
            "doctorService.doctor.user",
            "doctorService.service",
            "previousAppointment"
    })
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :appointmentId
              and appointment.patient.id = :patientId
            """)
    Optional<Appointment> findDetailedByIdAndPatientId(
            @Param("appointmentId") UUID appointmentId,
            @Param("patientId") UUID patientId
    );

    @EntityGraph(attributePaths = {
            "patient.user",
            "doctorService.doctor.user",
            "doctorService.service",
            "previousAppointment"
    })
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :appointmentId
              and appointment.doctorService.doctor.id = :doctorId
            """)
    Optional<Appointment> findDetailedByIdAndDoctorId(
            @Param("appointmentId") UUID appointmentId,
            @Param("doctorId") UUID doctorId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :appointmentId
              and appointment.patient.id = :patientId
            """)
    Optional<Appointment> findForUpdateByIdAndPatientId(
            @Param("appointmentId") UUID appointmentId,
            @Param("patientId") UUID patientId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.id = :appointmentId
              and appointment.doctorService.doctor.id = :doctorId
            """)
    Optional<Appointment> findForUpdateByIdAndDoctorId(
            @Param("appointmentId") UUID appointmentId,
            @Param("doctorId") UUID doctorId
    );

    @EntityGraph(attributePaths = {
            "patient.user",
            "doctorService.doctor.user",
            "doctorService.service"
    })
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.scheduledStartAt >= :rangeStart
              and appointment.scheduledStartAt < :rangeEnd
            order by appointment.scheduledStartAt asc, appointment.id asc
            """)
    List<Appointment> findDetailedByDoctorIdAndScheduledRange(
            @Param("doctorId") UUID doctorId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "patient.user",
            "doctorService.doctor.user",
            "doctorService.service"
    })
    @Query("""
            select appointment
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.scheduledStartAt >= :rangeStart
              and appointment.scheduledStartAt < :rangeEnd
            order by appointment.scheduledStartAt asc, appointment.id asc
            """)
    List<Appointment> findForUpdateByDoctorIdAndScheduledRange(
            @Param("doctorId") UUID doctorId,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd
    );

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

    @Query("""
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.status = :status
              and appointment.id <> :excludedAppointmentId
            """)
    boolean existsOtherByDoctorIdAndStatus(
            @Param("doctorId") UUID doctorId,
            @Param("status") AppointmentStatus status,
            @Param("excludedAppointmentId") UUID excludedAppointmentId
    );

    @Query("""
            select count(appointment) as todayAppointments,
                   coalesce(sum(case when appointment.status = :completedStatus
                                     then 1 else 0 end), 0) as completedToday,
                   coalesce(sum(case when appointment.status = :cancelledStatus
                                     then 1 else 0 end), 0) as cancelledToday,
                   coalesce(sum(case when appointment.status = :noShowStatus
                                     then 1 else 0 end), 0) as noShowToday
            from Appointment appointment
            where appointment.scheduledStartAt >= :rangeStart
              and appointment.scheduledStartAt < :rangeEnd
            """)
    AppointmentDailySummary summarizeScheduledRange(
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("completedStatus") AppointmentStatus completedStatus,
            @Param("cancelledStatus") AppointmentStatus cancelledStatus,
            @Param("noShowStatus") AppointmentStatus noShowStatus
    );
}
