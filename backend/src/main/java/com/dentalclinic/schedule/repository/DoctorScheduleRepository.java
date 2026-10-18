package com.dentalclinic.schedule.repository;

import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {

    @EntityGraph(attributePaths = "doctor")
    List<DoctorSchedule> findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(UUID doctorId);

    List<DoctorSchedule> findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
            UUID doctorId,
            short dayOfWeek
    );

    @EntityGraph(attributePaths = "doctor")
    Optional<DoctorSchedule> findByIdAndDoctor_Id(UUID scheduleId, UUID doctorId);

    boolean existsByDoctor_IdAndDayOfWeekAndSession(
            UUID doctorId,
            short dayOfWeek,
            ScheduleSession session
    );

    boolean existsByDoctor_IdAndDayOfWeekAndSessionAndIdNot(
            UUID doctorId,
            short dayOfWeek,
            ScheduleSession session,
            UUID scheduleId
    );
}
