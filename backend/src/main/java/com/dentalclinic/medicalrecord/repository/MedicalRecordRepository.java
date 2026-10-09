package com.dentalclinic.medicalrecord.repository;

import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);
}
