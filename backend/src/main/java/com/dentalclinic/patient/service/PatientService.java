package com.dentalclinic.patient.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.dto.PatientProfileResponse;
import com.dentalclinic.patient.dto.UpdatePatientProfileRequest;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final ClinicTimeProvider timeProvider;

    public PatientService(
            PatientRepository patientRepository,
            ClinicTimeProvider timeProvider
    ) {
        this.patientRepository = patientRepository;
        this.timeProvider = timeProvider;
    }

    @Transactional(readOnly = true)
    public PatientProfileResponse getMyProfile(UUID authenticatedUserId) {
        return toResponse(findByUserId(authenticatedUserId));
    }

    @Transactional
    public PatientProfileResponse updateMyProfile(
            UUID authenticatedUserId,
            UpdatePatientProfileRequest request
    ) {
        validateDateOfBirth(request.dateOfBirth());

        Patient patient = findByUserId(authenticatedUserId);
        User user = patient.getUser();
        user.setFullName(normalizeFullName(request.fullName()));

        patient.setDateOfBirth(request.dateOfBirth());
        patient.setGender(request.gender());
        patient.setPhone(normalizeNullable(request.phone()));
        patient.setAddress(normalizeNullable(request.address()));
        patient.setMedicalHistory(normalizeNullable(request.medicalHistory()));
        patient.setAllergies(normalizeNullable(request.allergies()));

        patientRepository.saveAndFlush(patient);
        return toResponse(patient);
    }

    private Patient findByUserId(UUID userId) {
        return patientRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
    }

    private void validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth != null && dateOfBirth.isAfter(timeProvider.today())) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Ngày sinh không được nằm trong tương lai"
            );
        }
    }

    private PatientProfileResponse toResponse(Patient patient) {
        User user = patient.getUser();
        return new PatientProfileResponse(
                patient.getId(),
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getPhone(),
                patient.getAddress(),
                patient.getMedicalHistory(),
                patient.getAllergies(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }

    private String normalizeFullName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ");
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
