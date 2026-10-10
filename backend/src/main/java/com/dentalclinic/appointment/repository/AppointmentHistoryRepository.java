package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.AppointmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, UUID> {

    @Query("""
            select history
            from AppointmentHistory history
            left join fetch history.changedBy
            where history.appointment.id = :appointmentId
            order by history.createdAt asc, history.id asc
            """)
    List<AppointmentHistory> findTimelineByAppointmentId(
            @Param("appointmentId") UUID appointmentId
    );
}
