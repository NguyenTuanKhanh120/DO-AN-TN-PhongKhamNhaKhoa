package com.dentalclinic.medicalrecord.service;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.mapper.MedicalRecordMapper;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PatientMedicalHistoryService {

    private final PatientRepository patientRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final MedicalRecordMapper medicalRecordMapper;

    public PatientMedicalHistoryService(
            PatientRepository patientRepository,
            MedicalRecordRepository medicalRecordRepository,
            MedicalRecordMapper medicalRecordMapper
    ) {
        this.patientRepository = patientRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.medicalRecordMapper = medicalRecordMapper;
    }

    @Transactional(readOnly = true)
    public PageResponse<PatientMedicalRecordResponse> getMedicalHistory(
            UUID authenticatedUserId,
            int page,
            int size
    ) {
        Patient patient = findPatient(authenticatedUserId);
        Page<PatientMedicalRecordResponse> records = medicalRecordRepository
                .findPatientHistory(
                        patient.getId(),
                        AppointmentStatus.COMPLETED,
                        PageRequest.of(page, size)
                )
                .map(medicalRecordMapper::toPatientResponse);
        return PageResponse.from(records);
    }

    @Transactional(readOnly = true)
    public PatientMedicalRecordResponse getMedicalRecord(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Patient patient = findPatient(authenticatedUserId);
        return medicalRecordRepository.findPatientHistoryDetail(
                        appointmentId,
                        patient.getId(),
                        AppointmentStatus.COMPLETED
                )
                .map(medicalRecordMapper::toPatientResponse)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy kết quả khám"));
    }

    private Patient findPatient(UUID authenticatedUserId) {
        return patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
    }
}
