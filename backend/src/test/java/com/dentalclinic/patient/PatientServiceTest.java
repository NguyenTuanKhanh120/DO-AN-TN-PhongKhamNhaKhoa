package com.dentalclinic.patient;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.dto.PatientProfileResponse;
import com.dentalclinic.patient.dto.UpdatePatientProfileRequest;
import com.dentalclinic.patient.entity.Gender;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.patient.service.PatientService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceTest {

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private ClinicTimeProvider timeProvider;

    private PatientService patientService;

    @BeforeEach
    void setUp() {
        patientService = new PatientService(patientRepository, timeProvider);
    }

    @Test
    void shouldReturnProfileBelongingToAuthenticatedUser() {
        Patient patient = patient();
        UUID authenticatedUserId = patient.getUser().getId();
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = patientService.getMyProfile(authenticatedUserId);

        verify(patientRepository).findByUserId(authenticatedUserId);
        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.userId()).isEqualTo(authenticatedUserId);
        assertThat(response.email()).isEqualTo("patient@example.com");
        assertThat(response.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(response.gender()).isEqualTo(Gender.UNSPECIFIED);
    }

    @Test
    void shouldUpdateOnlyAllowedProfileFields() {
        Patient patient = patient();
        UUID authenticatedUserId = patient.getUser().getId();
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 10, 8));
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));

        PatientProfileResponse response = patientService.updateMyProfile(
                authenticatedUserId,
                new UpdatePatientProfileRequest(
                        "  Trần   Thị   Bình  ",
                        LocalDate.of(2000, 5, 20),
                        Gender.FEMALE,
                        " 0901234567 ",
                        "  Thành phố Hồ Chí Minh ",
                        "   ",
                        " Penicillin "
                )
        );

        verify(patientRepository).saveAndFlush(patient);
        assertThat(patient.getUser().getFullName()).isEqualTo("Trần Thị Bình");
        assertThat(patient.getUser().getEmail()).isEqualTo("patient@example.com");
        assertThat(patient.getUser().getRole()).isEqualTo(Role.PATIENT);
        assertThat(patient.getPhone()).isEqualTo("0901234567");
        assertThat(patient.getAddress()).isEqualTo("Thành phố Hồ Chí Minh");
        assertThat(patient.getMedicalHistory()).isNull();
        assertThat(patient.getAllergies()).isEqualTo("Penicillin");
        assertThat(response.fullName()).isEqualTo("Trần Thị Bình");
    }

    @Test
    void shouldRejectDateOfBirthInFutureUsingClinicClock() {
        UUID authenticatedUserId = UUID.randomUUID();
        when(timeProvider.today()).thenReturn(LocalDate.of(2026, 10, 8));

        assertThatThrownBy(() -> patientService.updateMyProfile(
                authenticatedUserId,
                new UpdatePatientProfileRequest(
                        "Nguyễn Văn An",
                        LocalDate.of(2026, 10, 9),
                        Gender.MALE,
                        null,
                        null,
                        null,
                        null
                )
        ))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(patientRepository, never()).findByUserId(authenticatedUserId);
        verify(patientRepository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldFailWhenAuthenticatedPatientProfileDoesNotExist() {
        UUID authenticatedUserId = UUID.randomUUID();
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.getMyProfile(authenticatedUserId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bệnh nhân");
    }

    private Patient patient() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("hash");
        user.setFullName("Nguyễn Văn An");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
        patient.setGender(Gender.UNSPECIFIED);
        patient.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        patient.setUpdatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return patient;
    }
}
