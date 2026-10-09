package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.AppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.response.PageResponse;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
class AppointmentQueryServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    private AppointmentQueryService appointmentQueryService;

    @BeforeEach
    void setUp() {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(
                Clock.fixed(FIXED_NOW, CLINIC_ZONE)
        );
        appointmentQueryService = new AppointmentQueryService(
                appointmentRepository,
                patientRepository,
                doctorRepository,
                timeProvider,
                60
        );
    }

    @Test
    void shouldQueryAndMapOnlyAuthenticatedPatientAppointments() {
        UUID userId = UUID.randomUUID();
        Appointment appointment = appointment();
        Patient patient = appointment.getPatient();
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Appointment>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(
                List.of(appointment),
                PageRequest.of(0, 20),
                1
        ));

        PageResponse<AppointmentResponse> result = appointmentQueryService
                .getPatientAppointments(
                        userId,
                        "trám răng",
                        AppointmentStatus.BOOKED,
                        appointment.getDoctorService().getDoctor().getId(),
                        appointment.getDoctorService().getService().getId(),
                        LocalDate.of(2026, 10, 12),
                        LocalDate.of(2026, 10, 12),
                        0,
                        20
                );

        assertThat(result.totalElements()).isEqualTo(1);
        AppointmentResponse response = result.content().getFirst();
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.scheduledStartAt().toString())
                .isEqualTo("2026-10-12T10:00+07:00");
        assertThat(response.scheduledEndAt().toString())
                .isEqualTo("2026-10-12T11:00+07:00");
    }

    @Test
    void shouldNotRevealAnotherPatientsAppointment() {
        UUID userId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = appointment().getPatient();
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findDetailedByIdAndPatientId(
                appointmentId,
                patient.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentQueryService.getPatientAppointment(
                userId,
                appointmentId
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldRejectReversedDateRangeBeforeQueryingDatabase() {
        assertThatThrownBy(() -> appointmentQueryService.getAdminAppointments(
                null,
                null,
                null,
                null,
                null,
                LocalDate.of(2026, 10, 13),
                LocalDate.of(2026, 10, 12),
                0,
                20
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));

        verify(appointmentRepository, never()).findAll(
                org.mockito.ArgumentMatchers.<Specification<Appointment>>any(),
                any(Pageable.class)
        );
    }

    @Test
    void shouldUseChronologicalSortForDoctorTodayAppointments() {
        UUID userId = UUID.randomUUID();
        Doctor doctor = appointment().getDoctorService().getDoctor();
        when(doctorRepository.findByUserId(userId)).thenReturn(Optional.of(doctor));
        when(appointmentRepository.findAll(
                org.mockito.ArgumentMatchers.<Specification<Appointment>>any(),
                any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        appointmentQueryService.getDoctorTodayAppointments(
                userId,
                null,
                null,
                null,
                null,
                0,
                20
        );

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(appointmentRepository).findAll(
                org.mockito.ArgumentMatchers.<Specification<Appointment>>any(),
                pageableCaptor.capture()
        );
        Sort.Order timeOrder = pageableCaptor.getValue().getSort()
                .getOrderFor("scheduledStartAt");
        assertThat(timeOrder).isNotNull();
        assertThat(timeOrder.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    void shouldRequireDoctorProfileForDoctorQueries() {
        UUID userId = UUID.randomUUID();
        when(doctorRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> appointmentQueryService.getDoctorAppointments(
                userId,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bác sĩ");
    }

    private Appointment appointment() {
        User patientUser = new User();
        patientUser.setId(UUID.randomUUID());
        patientUser.setFullName("Bệnh nhân Nguyễn Văn Bình");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(patientUser);
        patient.setPhone("0901000001");

        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setFullName("Bác sĩ Nguyễn Minh Anh");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);
        doctor.setSpecialization("Nha khoa tổng quát");

        DentalService service = new DentalService();
        service.setId(UUID.randomUUID());
        service.setName("Trám răng");

        DoctorService doctorService = new DoctorService();
        doctorService.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        doctorService.setDoctor(doctor);
        doctorService.setService(service);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(Instant.parse("2026-10-12T03:00:00Z"));
        appointment.setStatus(AppointmentStatus.BOOKED);
        appointment.setCreatedAt(Instant.parse("2026-10-10T03:00:00Z"));
        appointment.setUpdatedAt(Instant.parse("2026-10-10T03:00:00Z"));
        return appointment;
    }
}
