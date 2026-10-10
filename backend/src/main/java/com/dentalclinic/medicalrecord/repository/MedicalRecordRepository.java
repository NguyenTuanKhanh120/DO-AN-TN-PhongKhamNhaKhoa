package com.dentalclinic.medicalrecord.repository;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, UUID> {

    Optional<MedicalRecord> findByAppointmentId(UUID appointmentId);

    boolean existsByAppointmentId(UUID appointmentId);

    @EntityGraph(attributePaths = {
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service"
    })
    @Query(
            value = """
                    select medicalRecord
                    from MedicalRecord medicalRecord
                    where medicalRecord.appointment.patient.id = :patientId
                      and medicalRecord.appointment.status = :status
                    order by medicalRecord.appointment.actualEndAt desc,
                             medicalRecord.appointment.scheduledStartAt desc,
                             medicalRecord.id asc
                    """,
            countQuery = """
                    select count(medicalRecord)
                    from MedicalRecord medicalRecord
                    where medicalRecord.appointment.patient.id = :patientId
                      and medicalRecord.appointment.status = :status
                    """
    )
    Page<MedicalRecord> findPatientHistory(
            @Param("patientId") UUID patientId,
            @Param("status") AppointmentStatus status,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "appointment.doctorService.doctor.user",
            "appointment.doctorService.service"
    })
    @Query("""
            select medicalRecord
            from MedicalRecord medicalRecord
            where medicalRecord.appointment.id = :appointmentId
              and medicalRecord.appointment.patient.id = :patientId
              and medicalRecord.appointment.status = :status
            """)
    Optional<MedicalRecord> findPatientHistoryDetail(
            @Param("appointmentId") UUID appointmentId,
            @Param("patientId") UUID patientId,
            @Param("status") AppointmentStatus status
    );
}
