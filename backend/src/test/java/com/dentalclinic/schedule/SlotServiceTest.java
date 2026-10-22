package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.Role;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 12);
    private static final LocalDate NEXT_MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant NOW = Instant.parse("2026-10-12T03:00:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DentalServiceRepository dentalServiceRepository;

    @Mock
    private DoctorServiceRepository doctorServiceRepository;

    @Mock
    private DoctorScheduleRepository doctorScheduleRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private SlotService slotService;

    @BeforeEach
    void setUp() {
        slotService = slotServiceAt(NOW);
    }

    private SlotService slotServiceAt(Instant now) {
        ClinicTimeProvider timeProvider = new ClinicTimeProvider(Clock.fixed(now, CLINIC_ZONE));
        return new SlotService(
                doctorRepository,
                dentalServiceRepository,
                doctorServiceRepository,
                doctorScheduleRepository,
                appointmentRepository,
                timeProvider,
                60,
                120
        );
    }

    @Test
    void shouldGenerateAvailableSlotsAndApplyAdvanceAndOccupiedFilters() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(
                schedule(doctor, ScheduleSession.MORNING, "08:00", "11:00"),
                schedule(doctor, ScheduleSession.AFTERNOON, "13:00", "17:00")
        ));
        Instant occupiedAt = toInstant(MONDAY, LocalTime.of(14, 0));
        when(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).thenReturn(List.of(occupiedAt));

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        );

        assertThat(slots).extracting(AvailableSlotResponse::startTime)
                .containsExactly(
                        LocalTime.of(13, 0),
                        LocalTime.of(15, 0),
                        LocalTime.of(16, 0)
                );
        assertThat(slots.getFirst().session()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(slots.getFirst().startAt().toString()).isEqualTo("2026-10-12T13:00+07:00");
        assertThat(slots.getFirst().endAt().toString()).isEqualTo("2026-10-12T14:00+07:00");
    }

    @Test
    void shouldGenerateThreeMorningAndFourAfternoonSlotsForDefaultSchedule() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(
                schedule(doctor, ScheduleSession.MORNING, "08:00", "11:00"),
                schedule(doctor, ScheduleSession.AFTERNOON, "13:00", "17:00")
        ));
        when(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(NEXT_MONDAY, LocalTime.MIN),
                toInstant(NEXT_MONDAY.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).thenReturn(List.of());

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(), dentalService.getId(), NEXT_MONDAY
        );

        assertThat(slots).hasSize(7);
        assertThat(slots).filteredOn(slot -> slot.session() == ScheduleSession.MORNING)
                .hasSize(3);
        assertThat(slots).filteredOn(slot -> slot.session() == ScheduleSession.AFTERNOON)
                .hasSize(4);
        assertThat(slots).extracting(AvailableSlotResponse::startTime)
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        LocalTime.of(15, 0),
                        LocalTime.of(16, 0)
                );
    }

    @Test
    void shouldKeepRecurringWeeklySlotsAvailableOnAnotherDate() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        LocalDate followingMonday = NEXT_MONDAY.plusWeeks(1);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.MORNING, "08:00", "11:00"
        )));
        when(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(followingMonday, LocalTime.MIN),
                toInstant(followingMonday.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).thenReturn(List.of());

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(), dentalService.getId(), followingMonday
        );

        assertThat(slots).hasSize(3);
        assertThat(slots).allMatch(slot -> slot.session() == ScheduleSession.MORNING);
    }

    @Test
    void shouldIgnoreLegacyScheduleThatDoesNotMatchHourlyClinicRules() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:30",
                "15:00"
        )));
        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        );

        assertThat(slots).isEmpty();
    }

    @Test
    void shouldReturnEmptyWithoutQueryingAppointmentsWhenDoctorDoesNotWorkThatDay() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of());

        assertThat(slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        )).isEmpty();

        verify(appointmentRepository, never()).findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        );
    }

    @Test
    void shouldRejectInactiveDoctorAccount() {
        Doctor doctor = doctor();
        doctor.getUser().setStatus(UserStatus.INACTIVE);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctor.getId(),
                UUID.randomUUID(),
                MONDAY
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Bác sĩ hiện không hoạt động");

        verify(dentalServiceRepository, never()).findById(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectDoctorWhoIsOnLeave() {
        Doctor doctor = doctor();
        doctor.setWorkingStatus(DoctorStatus.ON_LEAVE);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctor.getId(),
                UUID.randomUUID(),
                MONDAY
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Bác sĩ hiện không hoạt động");
    }

    @Test
    void shouldRejectInactiveDentalService() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(false);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Dịch vụ nha khoa hiện không hoạt động");
    }

    @Test
    void shouldRejectServiceNotAssignedToDoctor() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        DoctorServiceId mappingId = new DoctorServiceId(doctor.getId(), dentalService.getId());
        when(doctorServiceRepository.findById(mappingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Bác sĩ không cung cấp dịch vụ nha khoa đã chọn");
    }

    @Test
    void shouldReturnDoctorServiceWhenRequestedSlotIsAvailable() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        DoctorService mapping = stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:00",
                "17:00"
        )));
        Instant requestedStart = toInstant(MONDAY, LocalTime.of(13, 0));
        when(appointmentRepository.existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        )).thenReturn(false);

        DoctorService result = slotService.requireAvailableSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        );

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void shouldRejectOccupiedRequestedSlot() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:00",
                "17:00"
        )));
        Instant requestedStart = toInstant(MONDAY, LocalTime.of(14, 0));
        when(appointmentRepository.existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        )).thenReturn(true);

        assertThatThrownBy(() -> slotService.requireAvailableSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác");
    }

    @Test
    void shouldRejectRequestedTimeThatIsNotAConfiguredSlotStart() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:00",
                "17:00"
        )));

        assertThatThrownBy(() -> slotService.requireAvailableSlot(
                doctor.getId(),
                dentalService.getId(),
                toInstant(MONDAY, LocalTime.of(13, 30))
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Khung giờ không thuộc lịch làm việc của bác sĩ");

        verify(appointmentRepository, never()).existsOccupiedStart(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.of(13, 30)),
                AppointmentStatus.CANCELLED
        );
    }

    @Test
    void shouldRejectRequestedSlotInsideTwoHourAdvanceWindow() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.MORNING,
                "08:00",
                "11:00"
        )));

        assertThatThrownBy(() -> slotService.requireAvailableSlot(
                doctor.getId(),
                dentalService.getId(),
                toInstant(MONDAY, LocalTime.of(10, 0))
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Bạn cần đặt lịch trước giờ khám ít nhất 2 giờ");
    }

    @Test
    void shouldAllowRequestedSlotAtTwoHourAdvanceBoundary() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        DoctorService mapping = stubValidDoctorAndService(doctor, dentalService);
        SlotService boundaryService = slotServiceAt(Instant.parse("2026-10-12T04:00:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:00",
                "17:00"
        )));
        Instant requestedStart = toInstant(MONDAY, LocalTime.of(13, 0));
        when(appointmentRepository.existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        )).thenReturn(false);

        DoctorService result = boundaryService.requireAvailableSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        );

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void shouldAllowFutureRescheduleSlotInsideBookingAdvanceWindow() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        DoctorService mapping = stubValidDoctorAndService(doctor, dentalService);
        SlotService nearStartService = slotServiceAt(Instant.parse("2026-10-12T05:30:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.AFTERNOON,
                "13:00",
                "17:00"
        )));
        Instant requestedStart = toInstant(MONDAY, LocalTime.of(13, 0));
        when(appointmentRepository.existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        )).thenReturn(false);

        DoctorService result = nearStartService.requireAvailableRescheduleSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        );

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void shouldRejectPastRescheduleSlot() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.MORNING,
                "08:00",
                "11:00"
        )));
        Instant requestedStart = toInstant(MONDAY, LocalTime.of(9, 0));

        assertThatThrownBy(() -> slotService.requireAvailableRescheduleSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Khung giờ mới phải nằm trong tương lai");

        verify(appointmentRepository, never()).existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        );
    }

    @Test
    void shouldRejectOccupiedRescheduleSlotWithRollbackMessage() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.MORNING,
                "08:00",
                "11:00"
        )));
        Instant requestedStart = toInstant(NEXT_MONDAY, LocalTime.of(10, 0));
        when(appointmentRepository.existsOccupiedStart(
                doctor.getId(),
                requestedStart,
                AppointmentStatus.CANCELLED
        )).thenReturn(true);

        assertThatThrownBy(() -> slotService.requireAvailableRescheduleSlot(
                doctor.getId(),
                dentalService.getId(),
                requestedStart
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ mới vừa được người khác đặt. Lịch hiện tại của bạn vẫn "
                        + "được giữ nguyên. Vui lòng chọn khung giờ khác.");
    }

    @Test
    void shouldReportMissingDoctor() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctorId,
                UUID.randomUUID(),
                MONDAY
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bác sĩ");
    }

    @Test
    void shouldReportMissingDentalService() {
        Doctor doctor = doctor();
        UUID serviceId = UUID.randomUUID();
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(serviceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> slotService.getAvailableSlots(
                doctor.getId(),
                serviceId,
                MONDAY
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy dịch vụ nha khoa");
    }

    private DoctorService stubValidDoctorAndService(Doctor doctor, DentalService dentalService) {
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        when(doctorServiceRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));
        return mapping;
    }

    private void stubNoOccupiedAppointments(Doctor doctor) {
        when(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).thenReturn(List.of());
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
        doctor.setRating(new BigDecimal("4.5"));
        doctor.setExperienceYears(8);
        doctor.setWorkingStatus(DoctorStatus.ACTIVE);
        return doctor;
    }

    private DentalService dentalService(boolean active) {
        DentalService dentalService = new DentalService();
        dentalService.setId(UUID.randomUUID());
        dentalService.setName("Cạo vôi răng");
        dentalService.setPrice(new BigDecimal("300000"));
        dentalService.setActive(active);
        return dentalService;
    }

    private DoctorSchedule schedule(
            Doctor doctor,
            ScheduleSession session,
            String startTime,
            String endTime
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek((short) 1);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(startTime));
        schedule.setEndTime(LocalTime.parse(endTime));
        return schedule;
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }
}
