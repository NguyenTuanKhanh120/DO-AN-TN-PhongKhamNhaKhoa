package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.schedule.entity.ScheduleSession;
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
import java.time.LocalDate;
import java.util.Collection;
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
            select appointment.id as appointmentId,
                   appointment.appointmentDate as appointmentDate,
                   appointment.session as session,
                   appointment.queueNumber as queueNumber,
                   appointment.scheduledStartAt as scheduledStartAt
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.status in :activeStatuses
              and (
                    appointment.appointmentDate between :fromDate and :toDate
                    or (
                        appointment.appointmentDate is null
                        and appointment.scheduledStartAt >= :rangeStart
                        and appointment.scheduledStartAt < :rangeEnd
                    )
                  )
            order by appointment.scheduledStartAt asc, appointment.id asc
            """)
    List<AppointmentQueueOccupancy> findActiveQueueOccupancies(
            @Param("doctorId") UUID doctorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            @Param("rangeStart") Instant rangeStart,
            @Param("rangeEnd") Instant rangeEnd,
            @Param("activeStatuses") List<AppointmentStatus> activeStatuses
    );

    @Query("""
            select appointment.scheduledStartAt
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.scheduledStartAt >= :fromTime
              and appointment.status in :statuses
            order by appointment.scheduledStartAt asc
            """)
    List<Instant> findScheduledStartTimesByDoctorIdAndStatusesFrom(
            @Param("doctorId") UUID doctorId,
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("fromTime") Instant fromTime
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
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.doctorService.doctor.id = :doctorId
              and appointment.appointmentDate = :appointmentDate
              and appointment.session = :session
              and appointment.queueNumber = :queueNumber
              and appointment.status in :activeStatuses
            """)
    boolean existsActiveDoctorQueue(
            @Param("doctorId") UUID doctorId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("session") ScheduleSession session,
            @Param("queueNumber") short queueNumber,
            @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses
    );

    @Query("""
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.previousAppointment.id = :previousAppointmentId
              and appointment.status in :activeStatuses
            """)
    boolean existsActiveFollowUp(
            @Param("previousAppointmentId") UUID previousAppointmentId,
            @Param("activeStatuses") List<AppointmentStatus> activeStatuses
    );

    @Query("""
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.patient.id = :patientId
              and appointment.status in :activeStatuses
              and (
                    (
                        appointment.appointmentDate = :appointmentDate
                        and appointment.session = :session
                    )
                    or (
                        appointment.appointmentDate is null
                        and appointment.scheduledStartAt >= :sessionStartAt
                        and appointment.scheduledStartAt < :sessionEndAt
                    )
                  )
            """)
    boolean existsActivePatientSession(
            @Param("patientId") UUID patientId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("session") ScheduleSession session,
            @Param("sessionStartAt") Instant sessionStartAt,
            @Param("sessionEndAt") Instant sessionEndAt,
            @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses
    );

    @Query("""
            select (count(appointment) > 0)
            from Appointment appointment
            where appointment.id <> :excludedAppointmentId
              and appointment.patient.id = :patientId
              and appointment.status in :activeStatuses
              and (
                    (
                        appointment.appointmentDate = :appointmentDate
                        and appointment.session = :session
                    )
                    or (
                        appointment.appointmentDate is null
                        and appointment.scheduledStartAt >= :sessionStartAt
                        and appointment.scheduledStartAt < :sessionEndAt
                    )
                  )
            """)
    boolean existsOtherActivePatientSession(
            @Param("excludedAppointmentId") UUID excludedAppointmentId,
            @Param("patientId") UUID patientId,
            @Param("appointmentDate") LocalDate appointmentDate,
            @Param("session") ScheduleSession session,
            @Param("sessionStartAt") Instant sessionStartAt,
            @Param("sessionEndAt") Instant sessionEndAt,
            @Param("activeStatuses") Collection<AppointmentStatus> activeStatuses
    );

    @Query("""
            select count(distinct case
                              when appointment.status in (:checkedInStatus, :inProgressStatus, :completedStatus)
                              then appointment.patient.id else null
                          end) as patientsVisitedToday,
                   count(appointment) as todayAppointments,
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
            @Param("checkedInStatus") AppointmentStatus checkedInStatus,
            @Param("inProgressStatus") AppointmentStatus inProgressStatus,
            @Param("completedStatus") AppointmentStatus completedStatus,
            @Param("cancelledStatus") AppointmentStatus cancelledStatus,
            @Param("noShowStatus") AppointmentStatus noShowStatus
    );
}
