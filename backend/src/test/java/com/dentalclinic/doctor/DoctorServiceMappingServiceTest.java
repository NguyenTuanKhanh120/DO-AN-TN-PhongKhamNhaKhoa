package com.dentalclinic.doctor;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.doctor.service.DoctorServiceMappingService;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class DoctorServiceMappingServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DentalServiceRepository dentalServiceRepository;

    @Mock
    private DoctorServiceRepository doctorServiceRepository;

    @Captor
    private ArgumentCaptor<List<DoctorService>> removedMappingsCaptor;

    @Captor
    private ArgumentCaptor<List<DoctorService>> addedMappingsCaptor;

    private DoctorServiceMappingService service;

    @BeforeEach
    void setUp() {
        service = new DoctorServiceMappingService(
                doctorRepository,
                dentalServiceRepository,
                doctorServiceRepository
        );
    }

    @Test
    void shouldReturnServicesAssignedToPublicDoctor() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService("Cạo vôi răng");
        DoctorService mapping = mapping(doctor, dentalService);
        when(doctorRepository.findByIdAndUser_Status(doctor.getId(), UserStatus.ACTIVE))
                .thenReturn(Optional.of(doctor));
        when(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .thenReturn(List.of(mapping));

        List<DentalServiceResponse> response = service.getServicesByDoctor(doctor.getId());

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(dentalService.getId());
            assertThat(item.name()).isEqualTo("Cạo vôi răng");
            assertThat(item.imageUrl()).isEqualTo(dentalService.getImageUrl());
            assertThat(item.active()).isTrue();
        });
    }

    @Test
    void shouldHideMappingsWhenDoctorAccountIsInactive() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getServicesByDoctor(doctorId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bác sĩ");

        verify(doctorServiceRepository, never()).findAllWithServiceByDoctorId(doctorId);
    }

    @Test
    void shouldReturnActiveAccountDoctorsAssignedToService() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService("Trám răng");
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        when(doctorServiceRepository.findAllWithDoctorByServiceIdAndAccountStatus(
                dentalService.getId(),
                UserStatus.ACTIVE
        )).thenReturn(List.of(mapping(doctor, dentalService)));

        List<DoctorResponse> response = service.getDoctorsByService(dentalService.getId());

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(doctor.getId());
            assertThat(item.fullName()).isEqualTo(doctor.getUser().getFullName());
            assertThat(item.workingStatus()).isEqualTo(DoctorStatus.ACTIVE);
        });
    }

    @Test
    void shouldRejectUnknownServiceBeforeLookingUpMappings() {
        UUID serviceId = UUID.randomUUID();
        when(dentalServiceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDoctorsByService(serviceId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy dịch vụ nha khoa");

        verify(doctorServiceRepository, never())
                .findAllWithDoctorByServiceIdAndAccountStatus(serviceId, UserStatus.ACTIVE);
    }

    @Test
    void shouldReplaceOnlyChangedMappings() {
        Doctor doctor = doctor();
        DentalService removed = dentalService("Dịch vụ A");
        DentalService retained = dentalService("Dịch vụ B");
        DentalService added = dentalService("Dịch vụ C");
        DoctorService removedMapping = mapping(doctor, removed);
        DoctorService retainedMapping = mapping(doctor, retained);
        DoctorService addedMapping = mapping(doctor, added);

        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findAllById(org.mockito.ArgumentMatchers.<Iterable<UUID>>any()))
                .thenReturn(List.of(retained, added));
        when(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .thenReturn(
                        List.of(removedMapping, retainedMapping),
                        List.of(retainedMapping, addedMapping)
                );

        List<DentalServiceResponse> response = service.replaceDoctorServices(
                doctor.getId(),
                List.of(retained.getId(), added.getId())
        );

        verify(doctorServiceRepository).deleteAll(removedMappingsCaptor.capture());
        verify(doctorServiceRepository).flush();
        verify(doctorServiceRepository).saveAllAndFlush(addedMappingsCaptor.capture());
        assertThat(removedMappingsCaptor.getValue()).containsExactly(removedMapping);
        assertThat(addedMappingsCaptor.getValue()).singleElement().satisfies(mapping -> {
            assertThat(mapping.getId()).isEqualTo(new DoctorServiceId(doctor.getId(), added.getId()));
            assertThat(mapping.getDoctor()).isSameAs(doctor);
            assertThat(mapping.getService()).isSameAs(added);
        });
        assertThat(response).extracting(DentalServiceResponse::id)
                .containsExactly(retained.getId(), added.getId());
    }

    @Test
    void shouldAllowEmptyListToClearUnreferencedMappings() {
        Doctor doctor = doctor();
        DoctorService currentMapping = mapping(doctor, dentalService("Dịch vụ A"));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findAllById(
                org.mockito.ArgumentMatchers.<Iterable<UUID>>any()
        )).thenReturn(List.of());
        when(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .thenReturn(List.of(currentMapping), List.of());

        List<DentalServiceResponse> response = service.replaceDoctorServices(doctor.getId(), List.of());

        verify(doctorServiceRepository).deleteAll(List.of(currentMapping));
        verify(doctorServiceRepository).flush();
        verify(doctorServiceRepository, never()).saveAllAndFlush(
                org.mockito.ArgumentMatchers.<Iterable<DoctorService>>any()
        );
        assertThat(response).isEmpty();
    }

    @Test
    void shouldRejectDuplicateServiceIds() {
        Doctor doctor = doctor();
        UUID serviceId = UUID.randomUUID();
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> service.replaceDoctorServices(
                doctor.getId(),
                List.of(serviceId, serviceId)
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(dentalServiceRepository, never()).findAllById(
                org.mockito.ArgumentMatchers.<Iterable<UUID>>any()
        );
        verify(doctorServiceRepository, never()).saveAllAndFlush(
                org.mockito.ArgumentMatchers.<Iterable<DoctorService>>any()
        );
    }

    @Test
    void shouldRejectMissingServiceWithoutChangingMappings() {
        Doctor doctor = doctor();
        DentalService existing = dentalService("Dịch vụ tồn tại");
        UUID missingServiceId = UUID.randomUUID();
        List<UUID> requestedIds = List.of(existing.getId(), missingServiceId);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findAllById(
                org.mockito.ArgumentMatchers.<Iterable<UUID>>any()
        )).thenReturn(List.of(existing));

        assertThatThrownBy(() -> service.replaceDoctorServices(doctor.getId(), requestedIds))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy một hoặc nhiều dịch vụ nha khoa");

        verify(doctorServiceRepository, never()).deleteAll(
                org.mockito.ArgumentMatchers.<Iterable<DoctorService>>any()
        );
        verify(doctorServiceRepository, never()).saveAllAndFlush(
                org.mockito.ArgumentMatchers.<Iterable<DoctorService>>any()
        );
    }

    @Test
    void shouldReturnConflictWhenDatabaseRejectsMappingRemoval() {
        Doctor doctor = doctor();
        DoctorService currentMapping = mapping(doctor, dentalService("Dịch vụ đã dùng"));
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findAllById(
                org.mockito.ArgumentMatchers.<Iterable<UUID>>any()
        )).thenReturn(List.of());
        when(doctorServiceRepository.findAllWithServiceByDoctorId(doctor.getId()))
                .thenReturn(List.of(currentMapping));
        org.mockito.Mockito.doThrow(new DataIntegrityViolationException("FK conflict"))
                .when(doctorServiceRepository).flush();

        assertThatThrownBy(() -> service.replaceDoctorServices(doctor.getId(), List.of()))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);
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
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        doctor.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        doctor.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return doctor;
    }

    private DentalService dentalService(String name) {
        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName(name);
        dentalService.setDescription("Mô tả " + name);
        dentalService.setPrice(new BigDecimal("300000.00"));
        dentalService.setImageUrl("https://cdn.example.com/dental-services/service.webp");
        dentalService.setActive(true);
        dentalService.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        dentalService.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return dentalService;
    }

    private DoctorService mapping(Doctor doctor, DentalService dentalService) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        return mapping;
    }
}
