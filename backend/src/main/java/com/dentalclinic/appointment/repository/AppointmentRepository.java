package com.dentalclinic.appointment.repository;

import com.dentalclinic.appointment.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
}
