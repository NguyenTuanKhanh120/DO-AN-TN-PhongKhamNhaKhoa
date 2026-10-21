package com.dentalclinic.medicalrecord.repository;

import com.dentalclinic.medicalrecord.entity.MedicalRecordDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordDraftRepository extends JpaRepository<MedicalRecordDraft, UUID> {

    Optional<MedicalRecordDraft> findByAppointmentId(UUID appointmentId);

    @Modifying
    @Query("delete from MedicalRecordDraft draft where draft.appointment.id = :appointmentId")
    int deleteByAppointmentId(@Param("appointmentId") UUID appointmentId);
}
