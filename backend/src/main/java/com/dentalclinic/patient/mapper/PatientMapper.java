package com.dentalclinic.patient.mapper;

import com.dentalclinic.patient.dto.StaffPatientResponse;
import com.dentalclinic.patient.entity.Patient;
import org.springframework.stereotype.Component;

@Component
public class PatientMapper {

    public StaffPatientResponse toStaffResponse(Patient patient) {
        return new StaffPatientResponse(
                patient.getId(),
                patient.getFullName(),
                patient.getPhone(),
                patient.getDateOfBirth(),
                patient.getGender(),
                patient.getAddress(),
                patient.getMedicalHistory(),
                patient.getAllergies(),
                patient.getCreatedAt(),
                patient.getUpdatedAt()
        );
    }
}
