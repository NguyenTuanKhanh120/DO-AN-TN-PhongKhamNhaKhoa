package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.dto.BookAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentBookingService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentBookingServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T03:00:00Z");

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Mock
    private SlotService slotService;

    private AppointmentBookingService bookingService;

    @BeforeEach
    void setUp() {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE));
        bookingService = new AppointmentBookingService(
                patientRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                slotService,
                timeProvider,
                60
        );
    }

    @Test
    void shouldCreateBookedAppointmentAndBookHistoryInOneOperation() {
        Patient patient = patient();
        DoctorService mapping = mapping();
        BookAppointmentRequest request = request(mapping);
        when(patientRepository.findByUserId(patient.getUser().getId()))
                .thenReturn(Optional.of(patient));
        when(slotService.requireAvailableSlot(
                request.doctorId(),
                request.serviceId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(mapping);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(UUID.randomUUID());
            appointment.setCreatedAt(Instant.parse("2026-10-12T03:01:00Z"));
            return appointment;
        });
        when(appointmentHistoryRepository.saveAndFlush(any(AppointmentHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BookAppointmentResponse response = bookingService.book(
                patient.getUser().getId(),
                request
        );

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(appointmentCaptor.capture());
        Appointment appointment = appointmentCaptor.getValue();
        assertThat(appointment.getPatient()).isSameAs(patient);
        assertThat(appointment.getDoctorService()).isSameAs(mapping);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getScheduledStartAt())
                .isEqualTo(Instant.parse("2026-10-12T06:00:00Z"));

        ArgumentCaptor<AppointmentHistory> historyCaptor =
                ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.BOOK);
        assertThat(history.getChangedBy()).isSameAs(patient.getUser());
        assertThat(history.getDetails().path("status").asText()).isEqualTo("BOOKED");
        assertThat(history.getDetails().path("doctor_id").asText())
                .isEqualTo(mapping.getDoctor().getId().toString());
        assertThat(history.getDetails().path("service_id").asText())
                .isEqualTo(mapping.getService().getId().toString());
        assertThat(history.getDetails().path("scheduled_start_at").asText())
                .isEqualTo("2026-10-12T13:00+07:00");

        assertThat(response.patientId()).isEqualTo(patient.getId());
        assertThat(response.doctorId()).isEqualTo(mapping.getDoctor().getId());
        assertThat(response.serviceId()).isEqualTo(mapping.getService().getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.scheduledStartAt().toString()).isEqualTo("2026-10-12T13:00+07:00");
        assertThat(response.scheduledEndAt().toString()).isEqualTo("2026-10-12T14:00+07:00");
    }

    @Test
    void shouldResolvePatientFromAuthenticatedUser() {
        UUID userId = UUID.randomUUID();
        BookAppointmentRequest request = new BookAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00")
        );
        when(patientRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.book(userId, request))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy hồ sơ bệnh nhân");

        verify(slotService, never()).requireAvailableSlot(any(), any(), any());
    }

    @Test
    void shouldMapPostgreSqlUniqueViolationToConflict() {
        Patient patient = patient();
        DoctorService mapping = mapping();
        BookAppointmentRequest request = request(mapping);
        when(patientRepository.findByUserId(patient.getUser().getId()))
                .thenReturn(Optional.of(patient));
        when(slotService.requireAvailableSlot(
                request.doctorId(),
                request.serviceId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(mapping);
        SQLException postgresUniqueViolation = new SQLException(
                "duplicate key",
                "23505"
        );
        when(appointmentRepository.saveAndFlush(any(Appointment.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "unique constraint",
                        postgresUniqueViolation
                ));

        assertThatThrownBy(() -> bookingService.book(patient.getUser().getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác");

        verify(appointmentHistoryRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotMaskOtherDatabaseIntegrityFailuresAsSlotConflict() {
        Patient patient = patient();
        DoctorService mapping = mapping();
        BookAppointmentRequest request = request(mapping);
        when(patientRepository.findByUserId(patient.getUser().getId()))
                .thenReturn(Optional.of(patient));
        when(slotService.requireAvailableSlot(
                request.doctorId(),
                request.serviceId(),
                request.scheduledStartAt().toInstant()
        )).thenReturn(mapping);
        DataIntegrityViolationException failure = new DataIntegrityViolationException(
                "foreign key",
                new SQLException("foreign key violation", "23503")
        );
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenThrow(failure);

        assertThatThrownBy(() -> bookingService.book(patient.getUser().getId(), request))
                .isSameAs(failure);
    }

    private BookAppointmentRequest request(DoctorService mapping) {
        return new BookAppointmentRequest(
                mapping.getDoctor().getId(),
                mapping.getService().getId(),
                OffsetDateTime.parse("2026-10-12T13:00:00+07:00")
        );
    }

    private Patient patient() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("patient@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bệnh nhân Nguyễn Văn Bình");
        user.setRole(Role.PATIENT);
        user.setStatus(UserStatus.ACTIVE);

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
        return patient;
    }

    private DoctorService mapping() {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());

        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Cạo vôi răng");
        dentalService.setPrice(new BigDecimal("300000"));
        dentalService.setActive(true);

        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        return mapping;
    }
}
