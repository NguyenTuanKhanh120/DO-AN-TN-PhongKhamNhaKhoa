package com.dentalclinic.appointment;

import com.dentalclinic.appointment.dto.RescheduleAppointmentRequest;
import com.dentalclinic.appointment.dto.RescheduleAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.appointment.service.AppointmentRescheduleService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
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
import com.dentalclinic.user.entity.User;
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
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentRescheduleServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-11T21:59:00Z");
    private static final String SLOT_CONFLICT_MESSAGE = "Khung giờ mới vừa được người khác đặt. "
            + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.";

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

    private AppointmentRescheduleService rescheduleService;

    @BeforeEach
    void setUp() {
        rescheduleService = rescheduleServiceAt(FIXED_NOW);
    }

    private AppointmentRescheduleService rescheduleServiceAt(Instant now) {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(now, CLINIC_ZONE));
        rescheduleService = new AppointmentRescheduleService(
                patientRepository,
                doctorRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                slotService,
                new SchedulingPolicy(timeProvider),
                timeProvider
        );
        return rescheduleService;
    }

    @Test
    void shouldAllowAt0459AndWriteAudit() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        Instant newStart = toInstant(MONDAY, LocalTime.of(15, 30));
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        when(slotService.requireAvailableRescheduleCase(
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                2,
                appointment.getId()
        )).thenReturn(appointment.getDoctorService());

        RescheduleAppointmentResponse response = rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                new RescheduleAppointmentRequest(
                        MONDAY,
                        ScheduleSession.AFTERNOON,
                        2,
                        "  Bận   việc gia đình  "
                )
        );

        assertThat(appointment.getScheduledStartAt()).isEqualTo(newStart);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.id()).isEqualTo(appointment.getId());
        assertThat(response.status()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.oldEstimatedStartAt().toString())
                .isEqualTo("2026-10-12T14:00+07:00");
        assertThat(response.oldAppointmentDate()).isEqualTo(MONDAY);
        assertThat(response.oldSession()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(response.oldQueueNumber()).isEqualTo(1);
        assertThat(response.appointmentDate()).isEqualTo(MONDAY);
        assertThat(response.session()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(response.queueNumber()).isEqualTo(2);
        assertThat(response.estimatedStartAt().toString())
                .isEqualTo("2026-10-12T15:30+07:00");
        assertThat(response.sessionEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(response.reason()).isEqualTo("Bận việc gia đình");

        verify(appointmentRepository).saveAndFlush(appointment);
        ArgumentCaptor<AppointmentHistory> historyCaptor = ArgumentCaptor.forClass(
                AppointmentHistory.class
        );
        verify(appointmentHistoryRepository).saveAndFlush(historyCaptor.capture());
        AppointmentHistory history = historyCaptor.getValue();
        assertThat(history.getAppointment()).isSameAs(appointment);
        assertThat(history.getAction()).isEqualTo(AppointmentAction.RESCHEDULE);
        assertThat(history.getChangedBy()).isSameAs(patient.getUser());
        assertThat(history.getReason()).isEqualTo("Bận việc gia đình");
        assertThat(history.getDetails().path("old_appointment_date").asText())
                .isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("old_session").asText()).isEqualTo("AFTERNOON");
        assertThat(history.getDetails().path("old_queue_number").asInt()).isEqualTo(1);
        assertThat(history.getDetails().path("new_appointment_date").asText())
                .isEqualTo("2026-10-12");
        assertThat(history.getDetails().path("new_session").asText()).isEqualTo("AFTERNOON");
        assertThat(history.getDetails().path("new_queue_number").asInt()).isEqualTo(2);
        assertThat(history.getDetails().path("changed_at").asText())
                .isEqualTo("2026-10-12T04:59+07:00");
    }

    @Test
    void shouldRejectRescheduleAt0500() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        AppointmentRescheduleService atCutoffService = rescheduleServiceAt(
                Instant.parse("2026-10-11T22:00:00Z")
        );

        assertThatThrownBy(() -> atCutoffService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(toInstant(MONDAY, LocalTime.of(15, 30)))
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Không thể tự đổi lịch từ 05:00 ngày khám. "
                        + "Vui lòng giữ lịch hiện tại hoặc liên hệ phòng khám.");

        verify(slotService, never()).requireAvailableRescheduleCase(
                any(), any(), any(), any(), anyInt(), any()
        );
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectRescheduleWhenAppointmentIsNotBooked() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.COMPLETED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(toInstant(MONDAY, LocalTime.of(15, 30)))
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("trạng thái BOOKED");

        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
    }

    @Test
    void shouldRejectCurrentSlotAsNewSlot() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        stubOwnedAppointment(authenticatedUserId, patient, appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(appointment.getScheduledStartAt())
        )).isInstanceOfSatisfying(BusinessException.class, exception -> {
            assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
            assertThat(exception).hasMessage("Khung giờ mới phải khác khung giờ hiện tại");
        });

        verify(slotService, never()).requireAvailableRescheduleCase(
                any(), any(), any(), any(), anyInt(), any()
        );
    }

    @Test
    void shouldNotRevealAnotherPatientsAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        UUID appointmentId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findDetailedByIdAndPatientId(
                appointmentId,
                patient.getId()
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointmentId,
                request(toInstant(MONDAY, LocalTime.of(15, 30)))
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch hẹn");
    }

    @Test
    void shouldTranslateUniqueRaceToConflictWithoutWritingHistory() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        Instant newStart = toInstant(MONDAY, LocalTime.of(15, 30));
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        when(slotService.requireAvailableRescheduleCase(
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                2,
                appointment.getId()
        )).thenReturn(appointment.getDoctorService());
        doThrow(new DataIntegrityViolationException(
                "duplicate slot",
                new SQLException("duplicate slot", "23505")
        )).when(appointmentRepository).saveAndFlush(appointment);

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId,
                appointment.getId(),
                request(newStart)
        )).isInstanceOf(ConflictException.class)
                .hasMessage(SLOT_CONFLICT_MESSAGE);

        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    @Test
    void shouldRejectUnavailableCaseAndKeepOldAppointment() {
        UUID authenticatedUserId = UUID.randomUUID();
        Patient patient = patient(authenticatedUserId);
        Appointment appointment = appointment(
                patient,
                toInstant(MONDAY, LocalTime.of(14, 0)),
                AppointmentStatus.BOOKED
        );
        Instant oldStart = appointment.getScheduledStartAt();
        Instant newStart = toInstant(MONDAY, LocalTime.of(15, 30));
        stubOwnedAppointment(authenticatedUserId, patient, appointment);
        when(slotService.requireAvailableRescheduleCase(
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                2,
                appointment.getId()
        )).thenThrow(new ConflictException(
                SLOT_CONFLICT_MESSAGE
        ));

        assertThatThrownBy(() -> rescheduleService.reschedule(
                authenticatedUserId, appointment.getId(), request(newStart)
        )).isInstanceOf(ConflictException.class)
                .hasMessage(SLOT_CONFLICT_MESSAGE);

        assertThat(appointment.getScheduledStartAt()).isEqualTo(oldStart);
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).saveAndFlush(any(Appointment.class));
        verify(appointmentHistoryRepository, never()).saveAndFlush(any(AppointmentHistory.class));
    }

    private void stubOwnedAppointment(
            UUID authenticatedUserId,
            Patient patient,
            Appointment appointment
    ) {
        when(patientRepository.findByUserId(authenticatedUserId)).thenReturn(Optional.of(patient));
        when(appointmentRepository.findDetailedByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));
        when(doctorRepository.findForUpdateById(
                appointment.getDoctorService().getDoctor().getId()
        )).thenReturn(Optional.of(appointment.getDoctorService().getDoctor()));
        when(appointmentRepository.findForUpdateByIdAndPatientId(
                appointment.getId(),
                patient.getId()
        )).thenReturn(Optional.of(appointment));
    }

    private Patient patient(UUID userId) {
        User user = new User();
        user.setId(userId);
        user.setFullName("Bệnh nhân kiểm thử");

        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(user);
        patient.setFullName(user.getFullName());
        return patient;
    }

    private Appointment appointment(
            Patient patient,
            Instant scheduledStartAt,
            AppointmentStatus status
    ) {
        User doctorUser = new User();
        doctorUser.setId(UUID.randomUUID());
        doctorUser.setFullName("Bác sĩ kiểm thử");

        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(doctorUser);

        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Trám răng");

        DoctorService doctorService = new DoctorService();
        doctorService.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        doctorService.setDoctor(doctor);
        doctorService.setService(dentalService);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setScheduledStartAt(scheduledStartAt);
        LocalDate appointmentDate = scheduledStartAt.atZone(CLINIC_ZONE).toLocalDate();
        LocalTime startTime = scheduledStartAt.atZone(CLINIC_ZONE).toLocalTime();
        ScheduleSession session = startTime.isBefore(LocalTime.NOON)
                ? ScheduleSession.MORNING
                : ScheduleSession.AFTERNOON;
        LocalTime secondCaseStart = session == ScheduleSession.MORNING
                ? LocalTime.of(9, 30)
                : LocalTime.of(15, 30);
        appointment.setAppointmentDate(appointmentDate);
        appointment.setSession(session);
        appointment.setQueueNumber((short) (startTime.isBefore(secondCaseStart) ? 1 : 2));
        appointment.setStatus(status);
        return appointment;
    }

    private RescheduleAppointmentRequest request(Instant startAt) {
        LocalDate appointmentDate = startAt.atZone(CLINIC_ZONE).toLocalDate();
        LocalTime startTime = startAt.atZone(CLINIC_ZONE).toLocalTime();
        ScheduleSession session = startTime.isBefore(LocalTime.NOON)
                ? ScheduleSession.MORNING
                : ScheduleSession.AFTERNOON;
        int queueNumber = startTime.equals(
                session == ScheduleSession.MORNING
                        ? LocalTime.of(8, 0)
                        : LocalTime.of(14, 0)
        ) ? 1 : 2;
        return new RescheduleAppointmentRequest(
                appointmentDate,
                session,
                queueNumber,
                "Bận việc gia đình"
        );
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }
}
