package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.ForbiddenException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffRequest;
import com.dentalclinic.schedule.dto.CreateDoctorDayOffResponse;
import com.dentalclinic.schedule.dto.DoctorDayOffPeriod;
import com.dentalclinic.schedule.entity.DoctorDayOff;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.DoctorDayOffService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import com.dentalclinic.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorDayOffServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Instant NOW = Instant.parse("2026-10-19T01:00:00Z");
    private static final LocalDate OFF_DATE = LocalDate.of(2026, 10, 19);

    @Mock
    private DoctorRepository doctorRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private DoctorScheduleRepository doctorScheduleRepository;
    @Mock
    private DoctorDayOffRepository doctorDayOffRepository;
    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private AppointmentHistoryRepository appointmentHistoryRepository;

    private DoctorDayOffService service;

    @BeforeEach
    void setUp() {
        service = new DoctorDayOffService(
                doctorRepository,
                userRepository,
                doctorScheduleRepository,
                doctorDayOffRepository,
                appointmentRepository,
                appointmentHistoryRepository,
                new ClinicTimeProvider(Clock.fixed(NOW, CLINIC_ZONE))
        );
    }

    @ParameterizedTest
    @EnumSource(value = DoctorDayOffPeriod.class, names = {"MORNING", "AFTERNOON"})
    void shouldCreateOneRowForMorningOrAfternoon(DoctorDayOffPeriod period) {
        TestContext context = stubAuthorizedCreate(period);
        stubSavedDaysOff();

        CreateDoctorDayOffResponse response = service.create(
                context.admin().getId(),
                context.doctor().getId(),
                request(period)
        );

        ArgumentCaptor<List<DoctorDayOff>> captor = dayOffRowsCaptor();
        verify(doctorDayOffRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue()).singleElement().satisfies(dayOff -> {
            assertThat(dayOff.getDoctor()).isSameAs(context.doctor());
            assertThat(dayOff.getCreatedBy()).isSameAs(context.admin());
            assertThat(dayOff.getOffDate()).isEqualTo(OFF_DATE);
            assertThat(dayOff.getSession().name()).isEqualTo(period.name());
            assertThat(dayOff.getReason()).isEqualTo("Bác sĩ nghỉ đột xuất");
        });
        assertThat(response.createdDaysOff()).hasSize(1);
        assertThat(response.cancelledAppointmentCount()).isZero();
    }

    @Test
    void shouldCreateFullDayAtomicallyAsTwoRows() {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.FULL_DAY);
        stubSavedDaysOff();

        CreateDoctorDayOffResponse response = service.create(
                context.admin().getId(),
                context.doctor().getId(),
                request(DoctorDayOffPeriod.FULL_DAY)
        );

        ArgumentCaptor<List<DoctorDayOff>> captor = dayOffRowsCaptor();
        verify(doctorDayOffRepository).saveAllAndFlush(captor.capture());
        assertThat(captor.getValue())
                .extracting(DoctorDayOff::getSession)
                .containsExactly(ScheduleSession.MORNING, ScheduleSession.AFTERNOON);
        assertThat(response.createdDaysOff()).hasSize(2);
    }

    @Test
    void shouldRejectFullDayWhenEitherSessionAlreadyExists() {
        TestContext context = authorizedContext();
        when(doctorRepository.findForUpdateById(context.doctor().getId()))
                .thenReturn(Optional.of(context.doctor()));
        when(doctorDayOffRepository.existsByDoctor_IdAndOffDateAndSession(
                context.doctor().getId(), OFF_DATE, ScheduleSession.MORNING
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(
                context.admin().getId(),
                context.doctor().getId(),
                request(DoctorDayOffPeriod.FULL_DAY)
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("đã tồn tại");

        verify(doctorDayOffRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void shouldRejectMissingDoctor() {
        User admin = user(Role.ADMIN, UserStatus.ACTIVE);
        UUID doctorId = UUID.randomUUID();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(doctorRepository.findForUpdateById(doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(
                admin.getId(), doctorId, request(DoctorDayOffPeriod.MORNING)
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bác sĩ");
    }

    @Test
    void shouldRejectNonAdminBeforeReadingDoctorOrSchedules() {
        User patient = user(Role.PATIENT, UserStatus.ACTIVE);
        when(userRepository.findById(patient.getId())).thenReturn(Optional.of(patient));

        assertThatThrownBy(() -> service.create(
                patient.getId(), UUID.randomUUID(), request(DoctorDayOffPeriod.MORNING)
        )).isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(doctorRepository, doctorScheduleRepository, appointmentRepository);
    }

    @Test
    void shouldCancelFutureBookedAppointmentAndWriteSafeAuditWithoutChangingWeeklySchedule() {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.MORNING);
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        Appointment appointment = appointment(
                context.doctor(),
                AppointmentStatus.BOOKED,
                Instant.parse("2026-10-19T02:00:00Z")
        );
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(
                context.doctor().getId(),
                Instant.parse("2026-10-19T01:00:00Z"),
                Instant.parse("2026-10-19T04:00:00Z")
        )).thenReturn(List.of(appointment));
        stubSavedDaysOff();

        CreateDoctorDayOffResponse response = service.create(
                context.admin().getId(),
                context.doctor().getId(),
                request(DoctorDayOffPeriod.MORNING)
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(response.cancelledAppointmentCount()).isEqualTo(1);
        ArgumentCaptor<List<AppointmentHistory>> historyCaptor = historyRowsCaptor();
        verify(appointmentHistoryRepository).saveAll(historyCaptor.capture());
        assertThat(historyCaptor.getValue()).singleElement().satisfies(history -> {
            assertThat(history.getAppointment()).isSameAs(appointment);
            assertThat(history.getAction()).isEqualTo(AppointmentAction.CANCEL);
            assertThat(history.getChangedBy()).isSameAs(context.admin());
            assertThat(history.getReason()).isEqualTo(DoctorDayOffService.PUBLIC_CANCELLATION_REASON);
            assertThat(history.getDetails().path("source").asText()).isEqualTo("DOCTOR_DAY_OFF");
            assertThat(history.getDetails().path("old_status").asText()).isEqualTo("BOOKED");
            assertThat(history.getDetails().path("new_status").asText()).isEqualTo("CANCELLED");
            assertThat(history.getDetails().path("session").asText()).isEqualTo("MORNING");
        });
        verify(doctorScheduleRepository, never()).save(any(DoctorSchedule.class));
        verify(doctorScheduleRepository, never()).delete(any(DoctorSchedule.class));
    }

    @Test
    void shouldLeaveTerminalAppointmentsUnchanged() {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.MORNING);
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        List<Appointment> appointments = List.of(
                appointment(context.doctor(), AppointmentStatus.COMPLETED, NOW.plusSeconds(3600)),
                appointment(context.doctor(), AppointmentStatus.CANCELLED, NOW.plusSeconds(7200)),
                appointment(context.doctor(), AppointmentStatus.NO_SHOW, NOW.plusSeconds(10800))
        );
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(any(), any(), any()))
                .thenReturn(appointments);
        stubSavedDaysOff();

        service.create(context.admin().getId(), context.doctor().getId(), request(DoctorDayOffPeriod.MORNING));

        assertThat(appointments).extracting(Appointment::getStatus)
                .containsExactly(
                        AppointmentStatus.COMPLETED,
                        AppointmentStatus.CANCELLED,
                        AppointmentStatus.NO_SHOW
                );
        verify(appointmentRepository, never()).saveAll(anyList());
        verify(appointmentHistoryRepository, never()).saveAll(anyList());
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"CHECKED_IN", "IN_PROGRESS"})
    void shouldRejectActiveExaminationWithoutCancellingIt(AppointmentStatus status) {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.MORNING);
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        Appointment appointment = appointment(context.doctor(), status, NOW.plusSeconds(3600));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(any(), any(), any()))
                .thenReturn(List.of(appointment));

        assertThatThrownBy(() -> service.create(
                context.admin().getId(), context.doctor().getId(), request(DoctorDayOffPeriod.MORNING)
        )).isInstanceOf(ConflictException.class)
                .hasMessageContaining("CHECKED_IN hoặc IN_PROGRESS");

        assertThat(appointment.getStatus()).isEqualTo(status);
        verify(doctorDayOffRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    void previewShouldReportCancellationsAndBlockingAppointmentsWithoutWriting() {
        TestContext context = authorizedContext();
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        Appointment booked = appointment(
                context.doctor(), AppointmentStatus.BOOKED, NOW.plusSeconds(3600)
        );
        Appointment inProgress = appointment(
                context.doctor(), AppointmentStatus.IN_PROGRESS, NOW.plusSeconds(7200)
        );
        when(doctorRepository.findById(context.doctor().getId()))
                .thenReturn(Optional.of(context.doctor()));
        when(doctorDayOffRepository.existsByDoctor_IdAndOffDateAndSession(
                context.doctor().getId(), OFF_DATE, ScheduleSession.MORNING
        )).thenReturn(false);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findDetailedByDoctorIdAndScheduledRange(any(), any(), any()))
                .thenReturn(List.of(booked, inProgress));

        var response = service.preview(
                context.admin().getId(),
                context.doctor().getId(),
                request(DoctorDayOffPeriod.MORNING)
        );

        assertThat(response.appointmentsToCancelCount()).isEqualTo(1);
        assertThat(response.blockingAppointmentsCount()).isEqualTo(1);
        assertThat(response.canConfirm()).isFalse();
        verify(doctorDayOffRepository, never()).saveAllAndFlush(anyList());
        verify(appointmentRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldReportPastBookedAppointmentForManualReviewInsteadOfCancelling() {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.MORNING);
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        Appointment appointment = appointment(context.doctor(), AppointmentStatus.BOOKED, NOW);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(any(), any(), any()))
                .thenReturn(List.of(appointment));
        stubSavedDaysOff();

        CreateDoctorDayOffResponse response = service.create(
                context.admin().getId(), context.doctor().getId(), request(DoctorDayOffPeriod.MORNING)
        );

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        assertThat(response.manualReviewAppointmentCount()).isEqualTo(1);
        assertThat(response.cancelledAppointmentCount()).isZero();
    }

    @Test
    void shouldNotChangeAppointmentsWhenDayOffInsertFails() {
        TestContext context = stubAuthorizedCreate(DoctorDayOffPeriod.MORNING);
        DoctorSchedule schedule = schedule(context.doctor(), ScheduleSession.MORNING, "08:00", "11:00");
        Appointment appointment = appointment(context.doctor(), AppointmentStatus.BOOKED, NOW.plusSeconds(3600));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                context.doctor().getId(), (short) 1
        )).thenReturn(List.of(schedule));
        when(appointmentRepository.findForUpdateByDoctorIdAndScheduledRange(any(), any(), any()))
                .thenReturn(List.of(appointment));
        when(doctorDayOffRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> service.create(
                context.admin().getId(), context.doctor().getId(), request(DoctorDayOffPeriod.MORNING)
        )).isInstanceOf(ConflictException.class);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.BOOKED);
        verify(appointmentRepository, never()).saveAll(anyList());
        verify(appointmentHistoryRepository, never()).saveAll(anyList());
    }

    @Test
    void shouldDeleteOnlyDayOffAndNeverRestoreCancelledAppointments() {
        TestContext context = authorizedContext();
        DoctorDayOff dayOff = dayOff(context.doctor(), context.admin(), ScheduleSession.MORNING);
        when(doctorRepository.findForUpdateById(context.doctor().getId()))
                .thenReturn(Optional.of(context.doctor()));
        when(doctorDayOffRepository.findByIdAndDoctor_Id(dayOff.getId(), context.doctor().getId()))
                .thenReturn(Optional.of(dayOff));

        service.delete(context.admin().getId(), context.doctor().getId(), dayOff.getId());

        verify(doctorDayOffRepository).delete(dayOff);
        verify(doctorDayOffRepository).flush();
        verifyNoInteractions(appointmentRepository, appointmentHistoryRepository);
    }

    private TestContext stubAuthorizedCreate(DoctorDayOffPeriod period) {
        TestContext context = authorizedContext();
        when(doctorRepository.findForUpdateById(context.doctor().getId()))
                .thenReturn(Optional.of(context.doctor()));
        for (ScheduleSession session : period.sessions()) {
            when(doctorDayOffRepository.existsByDoctor_IdAndOffDateAndSession(
                    context.doctor().getId(), OFF_DATE, session
            )).thenReturn(false);
        }
        return context;
    }

    private TestContext authorizedContext() {
        User admin = user(Role.ADMIN, UserStatus.ACTIVE);
        Doctor doctor = doctor();
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        return new TestContext(admin, doctor);
    }

    private void stubSavedDaysOff() {
        when(doctorDayOffRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> {
            List<DoctorDayOff> rows = invocation.getArgument(0);
            rows.forEach(row -> row.setId(UUID.randomUUID()));
            return rows;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<DoctorDayOff>> dayOffRowsCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<AppointmentHistory>> historyRowsCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private CreateDoctorDayOffRequest request(DoctorDayOffPeriod period) {
        return new CreateDoctorDayOffRequest(OFF_DATE, period, "  Bác sĩ  nghỉ đột xuất  ");
    }

    private User user(Role role, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(role.name().toLowerCase() + "-" + user.getId() + "@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName(role == Role.ADMIN ? "Quản trị viên" : "Người dùng kiểm thử");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }

    private Doctor doctor() {
        Doctor doctor = new Doctor();
        doctor.setId(UUID.randomUUID());
        doctor.setUser(user(Role.DOCTOR, UserStatus.ACTIVE));
        return doctor;
    }

    private DoctorSchedule schedule(
            Doctor doctor,
            ScheduleSession session,
            String start,
            String end
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(start));
        schedule.setEndTime(LocalTime.parse(end));
        return schedule;
    }

    private Appointment appointment(Doctor doctor, AppointmentStatus status, Instant start) {
        User patientUser = user(Role.PATIENT, UserStatus.ACTIVE);
        Patient patient = new Patient();
        patient.setId(UUID.randomUUID());
        patient.setUser(patientUser);
        patient.setPhone("0901000001");

        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Trám răng");

        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);

        Appointment appointment = new Appointment();
        appointment.setId(UUID.randomUUID());
        appointment.setPatient(patient);
        appointment.setDoctorService(mapping);
        appointment.setScheduledStartAt(start);
        appointment.setStatus(status);
        return appointment;
    }

    private DoctorDayOff dayOff(Doctor doctor, User admin, ScheduleSession session) {
        DoctorDayOff dayOff = new DoctorDayOff();
        dayOff.setId(UUID.randomUUID());
        dayOff.setDoctor(doctor);
        dayOff.setOffDate(OFF_DATE);
        dayOff.setSession(session);
        dayOff.setReason("Lý do nội bộ");
        dayOff.setCreatedBy(admin);
        return dayOff;
    }

    private record TestContext(User admin, Doctor doctor) {
    }
}
