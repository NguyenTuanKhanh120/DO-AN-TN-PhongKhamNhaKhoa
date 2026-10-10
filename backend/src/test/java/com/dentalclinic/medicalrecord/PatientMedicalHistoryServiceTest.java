package com.dentalclinic.medicalrecord;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.mapper.MedicalRecordMapper;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.medicalrecord.service.PatientMedicalHistoryService;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientMedicalHistoryServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private MedicalRecordRepository medicalRecordRepository;

    @Mock
    private MedicalRecordMapper medicalRecordMapper;

    private PatientMedicalHistoryService patientMedicalHistoryService;

    @BeforeEach
    void setUp() {
        patientMedicalHistoryService = new PatientMedicalHistoryService(
                patientRepository,
                medicalRecordRepository,
                medicalRecordMapper
        );
    }

    @Test
    void shouldReturnOnlyCompletedHistoryForAuthenticatedPatient() {
        UUID userId = UUID.randomUUID();
        Patient patient = patient();
        MedicalRecord record = new MedicalRecord();
        PatientMedicalRecordResponse response = response();
        PageRequest pageRequest = PageRequest.of(1, 5);
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findPatientHistory(
                patient.getId(),
                AppointmentStatus.COMPLETED,
                pageRequest
        )).thenReturn(new PageImpl<>(List.of(record), pageRequest, 6));
        when(medicalRecordMapper.toPatientResponse(record)).thenReturn(response);

        PageResponse<PatientMedicalRecordResponse> result = patientMedicalHistoryService
                .getMedicalHistory(userId, 1, 5);

        assertThat(result.content()).containsExactly(response);
        assertThat(result.page()).isEqualTo(1);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.totalElements()).isEqualTo(6);
        assertThat(result.hasPrevious()).isTrue();
        verify(medicalRecordRepository).findPatientHistory(
                patient.getId(),
                AppointmentStatus.COMPLETED,
                pageRequest
        );
    }

    @Test
    void shouldReturnOwnedCompletedMedicalRecord() {
        UUID userId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = patient();
        MedicalRecord record = new MedicalRecord();
        PatientMedicalRecordResponse response = response();
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findPatientHistoryDetail(
                appointmentId,
                patient.getId(),
                AppointmentStatus.COMPLETED
        )).thenReturn(Optional.of(record));
        when(medicalRecordMapper.toPatientResponse(record)).thenReturn(response);

        PatientMedicalRecordResponse result = patientMedicalHistoryService
                .getMedicalRecord(userId, appointmentId);

        assertThat(result).isSameAs(response);
    }

    @Test
    void shouldHideMissingUnfinishedOrAnotherPatientsRecord() {
        UUID userId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = patient();
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(medicalRecordRepository.findPatientHistoryDetail(
                appointmentId,
                patient.getId(),
                AppointmentStatus.COMPLETED
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientMedicalHistoryService.getMedicalRecord(
                userId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy kết quả khám");

        verifyNoInteractions(medicalRecordMapper);
    }

    @Test
    void shouldRejectUserWithoutPatientProfile() {
        UUID userId = UUID.randomUUID();
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientMedicalHistoryService.getMedicalHistory(
                userId,
                0,
                20
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bệnh nhân");

        verify(medicalRecordRepository, never()).findPatientHistory(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        return patient;
    }

    private PatientMedicalRecordResponse response() {
        return new PatientMedicalRecordResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Bác sĩ Nguyễn",
                "Nha khoa tổng quát",
                UUID.randomUUID(),
                "Điều trị tủy",
                null,
                null,
                null,
                "Viêm tủy",
                "Điều trị tủy",
                null,
                null,
                List.of(),
                false,
                null,
                null,
                null,
                null
        );
    }
}
