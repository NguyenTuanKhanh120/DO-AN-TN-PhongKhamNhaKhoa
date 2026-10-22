package com.dentalclinic.schedule.repository;

import com.dentalclinic.schedule.entity.DoctorDayOff;
import com.dentalclinic.schedule.entity.ScheduleSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorDayOffRepository extends JpaRepository<DoctorDayOff, UUID> {

    boolean existsByDoctor_IdAndOffDateAndSession(
            UUID doctorId,
            LocalDate offDate,
            ScheduleSession session
    );

    @Query("""
            select dayOff.session
            from DoctorDayOff dayOff
            where dayOff.doctor.id = :doctorId
              and dayOff.offDate = :offDate
            """)
    List<ScheduleSession> findSessionsByDoctorIdAndOffDate(
            @Param("doctorId") UUID doctorId,
            @Param("offDate") LocalDate offDate
    );

    @EntityGraph(attributePaths = {"doctor.user", "createdBy"})
    @Query("""
            select dayOff
            from DoctorDayOff dayOff
            where dayOff.doctor.id = :doctorId
              and (:fromDate is null or dayOff.offDate >= :fromDate)
              and (:toDate is null or dayOff.offDate <= :toDate)
            order by dayOff.offDate asc, dayOff.session asc, dayOff.id asc
            """)
    List<DoctorDayOff> findByDoctorAndDateRange(
            @Param("doctorId") UUID doctorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @Query("""
            select dayOff
            from DoctorDayOff dayOff
            where dayOff.doctor.id = :doctorId
              and dayOff.offDate between :fromDate and :toDate
            order by dayOff.offDate asc, dayOff.session asc
            """)
    List<DoctorDayOff> findForAvailability(
            @Param("doctorId") UUID doctorId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    @EntityGraph(attributePaths = {"doctor.user", "createdBy"})
    Optional<DoctorDayOff> findByIdAndDoctor_Id(UUID id, UUID doctorId);
}
