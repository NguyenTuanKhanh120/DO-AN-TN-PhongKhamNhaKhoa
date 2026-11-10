package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.CreateWalkInAppointmentRequest;
import com.dentalclinic.appointment.dto.CreateWalkInAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.entity.BookingSource;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentWalkInService;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentWalkInServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant FIXED_NOW = Instant.parse("2026-10-12T02:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);

    @Mock
    private UserRepository userRepository;

    @Mock
    private PatientRepository patientRepository;

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    @Mock
    private SlotService slotService;

    private AppointmentWalkInService walkInService;

    @BeforeEach
    void setUp() {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE));
        SchedulingPolicy schedulingPolicy = new SchedulingPolicy(timeProvider);
        walkInService = new AppointmentWalkInService(
                userRepository,
                patientRepository,
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                slotService,
                schedulingPolicy,
                timeProvider
        );
    }

    @Test
    void shouldCreateWalkInAppointmentForUserlessPatientAndAuditStaff() {
        User staff = staff();
        Patient patient = patient();
        DoctorService mapping = mapping();
        CreateWalkInAppointmentRequest request = request(patient, mapping);
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorRepository.findForUpdateById(mapping.getDoctor().getId()))
                .thenReturn(Optional.of(mapping.getDoctor()));
        when(slotService.requireAvailableWalkInCase(
                mapping.getDoctor().getId(),
                mapping.getService().getId(),
                TODAY,
                ScheduleSession.MORNING,
                2
        )).thenReturn(mapping);
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenAnswer(invocation -> {
            Appointment appointment = invocation.getArgument(0);
            appointment.setId(UUID.randomUUID());
            appointment.setCreatedAt(FIXED_NOW.plusSeconds(30));
            return appointment;
        });
        when(appointmentHistoryRepository.saveAndFlush(any(AppointmentHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CreateWalkInAppointmentResponse response = walkInService.create(staff.getId(), request);

        ArgumentCaptor<Appointment> appointmentCaptor = ArgumentCaptor.forClass(Appointment.class);
        verify(appointmentRepository).saveAndFlush(appointmentCaptor.capture());
        Appointment appointment = appointmentCaptor.getValue();
        assertThat(appointment.getPatient()).isSameAs(patient);
        assertThat(appointment.getPatient().getUser()).isNull();
        assertThat(appointment.getDoctorService()).isSameAs(mapping);
        assertThat(appointment.getAppointmentDate()).isEqualTo(TODAY);
        assertThat(appointment.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(appointment.getQueueNumber()).isEqualTo((short) 2);
        assertThat(appointment.getScheduledStartAt())
                .isEqualTo(Instant.parse("2026-10-12T02:30:00Z"));
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(appointment.getBookingSource()).isEqualTo(BookingSource.WALK_IN);

        ArgumentCaptor<AppointmentHistory> historyCaptor =
                ArgumentCaptor.forClass(AppointmentHistory.class);
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getChangedBy()).isSameAs(staff);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.BOOK);
        assertThat(history.getDetails().path("booking_source").asText()).isEqualTo("WALK_IN");
        assertThat(history.getDetails().path("created_by_role").asText()).isEqualTo("STAFF");
        assertThat(history.getDetails().path("appointment_date").asText())
                .isEqualTo(TODAY.toString());
        assertThat(history.getDetails().path("session").asText()).isEqualTo("MORNING");
        assertThat(history.getDetails().path("queue_number").asInt()).isEqualTo(2);

        assertThat(response.bookingSource()).isEqualTo(BookingSource.WALK_IN);
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.appointmentDate()).isEqualTo(TODAY);
        assertThat(response.estimatedStartAt().toString())
                .isEqualTo("2026-10-12T09:30+07:00");
    }

    @Test
    void shouldRejectInactiveOrNonStaffAccount() {
        User doctor = staff();
        doctor.setRole(Role.DOCTOR);
        CreateWalkInAppointmentRequest request = new CreateWalkInAppointmentRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ScheduleSession.MORNING,
                1
        );
        when(userRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> walkInService.create(doctor.getId(), request))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Chỉ nhân viên đang hoạt động mới được tạo lịch WALK_IN");

        verify(patientRepository, never()).findById(any());
    }

    @Test
    void shouldRejectPatientWithActiveAppointmentInSameSession() {
        User staff = staff();
        Patient patient = patient();
        DoctorService mapping = mapping();
        CreateWalkInAppointmentRequest request = request(patient, mapping);
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(appointmentRepository.existsActivePatientSession(
                eq(patient.getId()),
                eq(TODAY),
                eq(ScheduleSession.MORNING),
                any()
        )).thenReturn(true);

        assertThatThrownBy(() -> walkInService.create(staff.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Bệnh nhân đã có một lịch khám đang hoạt động trong buổi này");

        verify(slotService, never()).requireAvailableWalkInCase(
                any(), any(), any(), any(), anyInt()
        );
        verify(appointmentRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldMapPostgreSqlUniqueViolationToWalkInConflict() {
        User staff = staff();
        Patient patient = patient();
        DoctorService mapping = mapping();
        CreateWalkInAppointmentRequest request = request(patient, mapping);
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(patientRepository.findById(patient.getId())).thenReturn(Optional.of(patient));
        when(doctorRepository.findForUpdateById(mapping.getDoctor().getId()))
                .thenReturn(Optional.of(mapping.getDoctor()));
        when(slotService.requireAvailableWalkInCase(
                mapping.getDoctor().getId(),
                mapping.getService().getId(),
                TODAY,
                ScheduleSession.MORNING,
                2
        )).thenReturn(mapping);
        DataIntegrityViolationException conflict = new DataIntegrityViolationException(
                "unique violation",
                new SQLException("duplicate key", "23505")
        );
        when(appointmentRepository.saveAndFlush(any(Appointment.class))).thenThrow(conflict);

        assertThatThrownBy(() -> walkInService.create(staff.getId(), request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Ca khám vừa được đặt. Vui lòng chọn ca khác.");

        verify(appointmentHistoryRepository, never()).saveAndFlush(any());
    }

    private User staff() {
        User staff = new User();
        staff.setId(UUID.randomUUID());
        staff.setEmail("staff@example.com");
        staff.setPasswordHash("not-used");
        staff.setFullName("Nhân viên tiếp nhận");
        staff.setRole(Role.STAFF);
        staff.setStatus(UserStatus.ACTIVE);
        return staff;
    }

    private Patient patient() {
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setFullName("Bệnh nhân tại quầy");
        patient.setUser(null);
        return patient;
    }

    private DoctorService mapping() {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        DentalService service = new DentalService();
        service.setId(UUID.randomUUID());
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), service.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(service);
        return mapping;
    }

    private CreateWalkInAppointmentRequest request(
            Patient patient,
            DoctorService mapping
    ) {
        return new CreateWalkInAppointmentRequest(
                patient.getId(),
                mapping.getDoctor().getId(),
                mapping.getService().getId(),
                ScheduleSession.MORNING,
                2
        );
    }
}
