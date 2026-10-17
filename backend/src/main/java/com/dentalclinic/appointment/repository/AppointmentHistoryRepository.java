package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentAction;
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

    @Query("""
            select history
            from AppointmentHistory history
            left join fetch history.changedBy
            where history.appointment.id in :appointmentIds
              and history.action = :action
            order by history.createdAt desc, history.id desc
            """)
    List<AppointmentHistory> findByAppointmentIdsAndAction(
            @Param("appointmentIds") List<UUID> appointmentIds,
            @Param("action") AppointmentAction action
    );
}
