package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.AppointmentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppointmentHistoryRepository extends JpaRepository<AppointmentHistory, UUID> {
}
