package com.dentalclinic.doctor;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.doctor.dto.AdminDoctorResponse;
import com.dentalclinic.doctor.dto.CreateDoctorRequest;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.dto.UpdateDoctorRequest;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.service.DoctorManagementService;
import com.dentalclinic.schedule.service.DoctorScheduleService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorManagementServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private DoctorScheduleService doctorScheduleService;

    private DoctorManagementService service;

    @BeforeEach
    void setUp() {
        service = new DoctorManagementService(
                doctorRepository,
                userRepository,
                passwordEncoder,
                doctorScheduleService
        );
    }

    @Test
    void shouldCreateDoctorUserAndProfileWithSafeDefaults() {
        CreateDoctorRequest request = createRequest(" Doctor@Example.com ", "GP-001");
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(false);
        when(doctorRepository.existsByLicenseNumber("GP-001")).thenReturn(false);
        when(passwordEncoder.encode("StrongPassword123!")).thenReturn("bcrypt-hash");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(doctorRepository.saveAndFlush(any(Doctor.class))).thenAnswer(invocation -> {
            Doctor doctor = invocation.getArgument(0);
            doctor.setId(UUID.randomUUID());
            return doctor;
        });

        AdminDoctorResponse response = service.createDoctor(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        ArgumentCaptor<Doctor> doctorCaptor = ArgumentCaptor.forClass(Doctor.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        verify(doctorRepository).saveAndFlush(doctorCaptor.capture());

        User user = userCaptor.getValue();
        Doctor doctor = doctorCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo("doctor@example.com");
        assertThat(user.getPasswordHash()).isEqualTo("bcrypt-hash");
        assertThat(user.getFullName()).isEqualTo("Bác sĩ Nguyễn Văn An");
        assertThat(user.getRole()).isEqualTo(Role.DOCTOR);
        assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(doctor.getUser()).isSameAs(user);
        assertThat(doctor.getSpecialization()).isEqualTo("Nha khoa tổng quát");
        assertThat(doctor.getLicenseNumber()).isEqualTo("GP-001");
        assertThat(doctor.getBio()).isEqualTo("Bác sĩ kiểm thử");
        assertThat(doctor.getRating()).isEqualByComparingTo("0.0");
        assertThat(doctor.getExperienceYears()).isEqualTo(8);
        assertThat(doctor.getWorkingStatus()).isEqualTo(DoctorStatus.ACTIVE);
        assertThat(response.email()).isEqualTo("doctor@example.com");
        assertThat(response.workingStatus()).isEqualTo(DoctorStatus.ACTIVE);
        verify(doctorScheduleService).createDefaultWeeklySchedule(doctor);
    }

    @Test
    void shouldRejectDuplicateEmailBeforePersisting() {
        CreateDoctorRequest request = createRequest("doctor@example.com", "GP-001");
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.createDoctor(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.EMAIL_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any());
        verify(doctorRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectDuplicateLicenseBeforeCreatingUser() {
        CreateDoctorRequest request = createRequest("doctor@example.com", "GP-001");
        when(userRepository.existsByEmail("doctor@example.com")).thenReturn(false);
        when(doctorRepository.existsByLicenseNumber("GP-001")).thenReturn(true);

        assertThatThrownBy(() -> service.createDoctor(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.LICENSE_NUMBER_ALREADY_EXISTS);

        verify(userRepository, never()).saveAndFlush(any());
        verify(doctorRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectPasswordLongerThanBcryptByteLimit() {
        CreateDoctorRequest request = new CreateDoctorRequest(
                "doctor@example.com",
                "ộ".repeat(30),
                "Bác sĩ Nguyễn Văn An",
                "Nha khoa tổng quát",
                "GP-001",
                null,
                8
        );

        assertThatThrownBy(() -> service.createDoctor(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.PASSWORD_TOO_LONG);

        verify(userRepository, never()).existsByEmail(any());
    }

    @Test
    void shouldUpdateDoctorProfileWithoutChangingAccountIdentityOrStatus() {
        Doctor doctor = doctor();
        UUID doctorId = doctor.getId();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(doctorRepository.existsByLicenseNumberAndIdNot("GP-NEW", doctorId)).thenReturn(false);

        AdminDoctorResponse response = service.updateDoctor(
                doctorId,
                new UpdateDoctorRequest(
                        "  Bác sĩ   Trần Thị Bình  ",
                        " Chỉnh nha ",
                        " GP-NEW ",
                        "  Tiểu sử mới  ",
                        new BigDecimal("4.5"),
                        12
                )
        );

        assertThat(doctor.getUser().getFullName()).isEqualTo("Bác sĩ Trần Thị Bình");
        assertThat(doctor.getUser().getEmail()).isEqualTo("doctor@example.com");
        assertThat(doctor.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(doctor.getSpecialization()).isEqualTo("Chỉnh nha");
        assertThat(doctor.getLicenseNumber()).isEqualTo("GP-NEW");
        assertThat(doctor.getBio()).isEqualTo("Tiểu sử mới");
        assertThat(doctor.getRating()).isEqualByComparingTo("4.5");
        assertThat(doctor.getExperienceYears()).isEqualTo(12);
        assertThat(response.fullName()).isEqualTo("Bác sĩ Trần Thị Bình");
        verify(doctorRepository).saveAndFlush(doctor);
    }

    @Test
    void shouldUpdateOnlyDoctorWorkingStatus() {
        Doctor doctor = doctor();
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        AdminDoctorResponse response = service.updateWorkingStatus(doctor.getId(), DoctorStatus.ON_LEAVE);

        assertThat(doctor.getWorkingStatus()).isEqualTo(DoctorStatus.ON_LEAVE);
        assertThat(doctor.getUser().getStatus()).isEqualTo(UserStatus.ACTIVE);
        assertThat(response.workingStatus()).isEqualTo(DoctorStatus.ON_LEAVE);
        assertThat(response.accountStatus()).isEqualTo(UserStatus.ACTIVE);
        verify(doctorRepository).saveAndFlush(doctor);
    }

    @Test
    void shouldReturnPublicPagedDoctors() {
        Doctor doctor = doctor();
        PageRequest pageable = PageRequest.of(
                0,
                10,
                Sort.by(Sort.Order.asc("user.fullName"), Sort.Order.asc("id"))
        );
        when(doctorRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Doctor>>any(),
                eq(pageable)
        )).thenReturn(new PageImpl<>(List.of(doctor), pageable, 1));

        PageResponse<DoctorResponse> response = service.getDoctors(
                "nguyễn",
                "Nha khoa tổng quát",
                DoctorStatus.ACTIVE,
                0,
                10
        );

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(doctor.getId());
            assertThat(item.fullName()).isEqualTo(doctor.getUser().getFullName());
            assertThat(item.licenseNumber()).isEqualTo(doctor.getLicenseNumber());
        });
    }

    @Test
    void shouldHideInactiveAccountFromPublicDetail() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDoctor(doctorId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bác sĩ");
    }

    private CreateDoctorRequest createRequest(String email, String licenseNumber) {
        return new CreateDoctorRequest(
                email,
                "StrongPassword123!",
                "  Bác sĩ   Nguyễn Văn An  ",
                "  Nha khoa tổng quát  ",
                licenseNumber,
                "  Bác sĩ kiểm thử  ",
                8
        );
    }

    private Doctor doctor() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bác sĩ Nguyễn Văn An");
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user);
        doctor.setSpecialization("Nha khoa tổng quát");
        doctor.setLicenseNumber("GP-001");
        doctor.setBio("Bác sĩ kiểm thử");
        doctor.setRating(new BigDecimal("4.0"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        doctor.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        doctor.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return doctor;
    }
}
