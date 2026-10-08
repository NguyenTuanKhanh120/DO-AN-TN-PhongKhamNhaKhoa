package com.dentalclinic.schedule.repository;

import com.dentalclinic.schedule.entity.DoctorSchedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DoctorScheduleRepository extends JpaRepository<DoctorSchedule, UUID> {
}
