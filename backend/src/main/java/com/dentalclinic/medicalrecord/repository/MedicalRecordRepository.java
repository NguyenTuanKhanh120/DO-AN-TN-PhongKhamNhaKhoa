package com.dentalclinic.medicalrecord.repository;

import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {
}
