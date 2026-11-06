package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentQueueOccupancy;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.schedule.dto.CalendarAvailabilityStatus;
import com.dentalclinic.schedule.dto.DoctorCalendarResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.schedule.service.SchedulingPolicy;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorCalendarServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-18T21:00:00Z");

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
    private Doctor doctor;
    private DentalService dentalService;

    @BeforeEach
    void setUp() {
        slotService = slotServiceAt(FIXED_NOW);
        doctor = doctor();
        dentalService = dentalService();
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
                new SchedulingPolicy(timeProvider),
                60,
                120
        );
    }

    @Test
    void shouldBuildRollingThirtyDayCalendarWithScheduleAndAppointmentRangeQueries() {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        when(doctorServiceRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));
        List<DoctorSchedule> schedules = defaultSchedules(doctor);
        LocalDate fullyBookedDate = MONDAY.plusDays(3);
        List<AppointmentQueueOccupancy> occupancies = new ArrayList<>();
        occupancies.add(occupancy(MONDAY, ScheduleSession.AFTERNOON, 1));
        List.of(ScheduleSession.MORNING, ScheduleSession.AFTERNOON).forEach(session -> {
            occupancies.add(occupancy(fullyBookedDate, session, 1));
            occupancies.add(occupancy(fullyBookedDate, session, 2));
        });

        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(schedules);
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY.plusDays(29),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(30), LocalTime.MIN),
                activeStatuses()
        )).thenReturn(occupancies);

        DoctorCalendarResponse response = slotService.getCalendar(
                doctor.getId(),
                dentalService.getId(),
                null,
                null
        );

        assertThat(response.fromDate()).isEqualTo(MONDAY);
        assertThat(response.toDate()).isEqualTo(MONDAY.plusDays(29));
        assertThat(response.days()).hasSize(30);
        assertThat(CalendarAvailabilityStatus.values())
                .extracting(Enum::name)
                .doesNotContain("DAY_OFF");
        assertThat(response.days().getFirst().sessions()).satisfiesExactly(
                morning -> {
                    assertThat(morning.session()).isEqualTo(ScheduleSession.MORNING);
                    assertThat(morning.status()).isEqualTo(CalendarAvailabilityStatus.AVAILABLE);
                    assertThat(morning.availableSlotCount()).isEqualTo(2);
                    assertThat(morning.availableSlots()).extracting(slot -> slot.startAt().toLocalTime())
                            .containsExactly(LocalTime.of(8, 0), LocalTime.of(9, 30));
                },
                afternoon -> {
                    assertThat(afternoon.session()).isEqualTo(ScheduleSession.AFTERNOON);
                    assertThat(afternoon.availableSlotCount()).isEqualTo(1);
                    assertThat(afternoon.availableSlots()).extracting(slot -> slot.startAt().toLocalTime())
                            .containsExactly(LocalTime.of(15, 30));
                }
        );
        assertThat(response.days().get(1).status()).isEqualTo(CalendarAvailabilityStatus.AVAILABLE);
        assertThat(response.days().get(1).sessions()).satisfiesExactly(
                morning -> assertThat(morning.availableSlotCount()).isEqualTo(2),
                afternoon -> assertThat(afternoon.availableSlotCount()).isEqualTo(2)
        );
        assertThat(response.days().get(3).status()).isEqualTo(CalendarAvailabilityStatus.FULL);
        assertThat(response.days().get(3).sessions()).allSatisfy(session -> {
            assertThat(session.status()).isEqualTo(CalendarAvailabilityStatus.FULL);
            assertThat(session.availableSlots()).isEmpty();
        });
        assertThat(response.days())
                .filteredOn(day -> day.dayOfWeek() == 7)
                .allSatisfy(day -> {
                    assertThat(day.status()).isEqualTo(CalendarAvailabilityStatus.NOT_WORKING);
                    assertThat(day.sessions()).allSatisfy(session ->
                            assertThat(session.availableSlots()).isEmpty()
                    );
                });

        verify(doctorScheduleRepository, times(1))
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctor.getId());
        verify(appointmentRepository, times(1)).findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY.plusDays(29),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(30), LocalTime.MIN),
                activeStatuses()
        );
    }

    @Test
    void shouldRejectCalendarOutsideRollingThirtyDayWindow() {
        assertThatThrownBy(() -> slotService.getCalendar(
                doctor.getId(),
                dentalService.getId(),
                MONDAY.minusDays(1),
                MONDAY.plusDays(1)
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Từ ngày không được nằm trước ngày hiện tại");

        assertThatThrownBy(() -> slotService.getCalendar(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                MONDAY.plusDays(30)
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Lịch chỉ hỗ trợ xem trong 30 ngày tính từ ngày hiện tại");
    }

    @Test
    void shouldReportAllSessionsUnavailableAtDailyCutoff() {
        slotService = slotServiceAt(Instant.parse("2026-10-19T05:00:00Z"));
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        when(doctorServiceRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));
        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(List.of(
                schedule(doctor, (short) 1, ScheduleSession.MORNING, "08:00", "11:00"),
                schedule(doctor, (short) 1, ScheduleSession.AFTERNOON, "14:00", "17:00")
        ));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                activeStatuses()
        )).thenReturn(List.of());

        DoctorCalendarResponse response = slotService.getCalendar(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                MONDAY
        );

        assertThat(response.days().getFirst().status())
                .isEqualTo(CalendarAvailabilityStatus.UNAVAILABLE);
        assertThat(response.days().getFirst().sessions()).satisfiesExactly(
                morning -> assertThat(morning.status())
                        .isEqualTo(CalendarAvailabilityStatus.UNAVAILABLE),
                afternoon -> assertThat(afternoon.status())
                        .isEqualTo(CalendarAvailabilityStatus.UNAVAILABLE)
        );
    }

    @Test
    void shouldBuildDoctorOnlyCalendarWithoutLookingUpDentalServiceMapping() {
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(List.of(
                schedule(doctor, (short) 1, ScheduleSession.MORNING, "08:00", "11:00")
        ));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(1), LocalTime.MIN),
                activeStatuses()
        )).thenReturn(List.of());

        DoctorCalendarResponse response = slotService.getDoctorCalendar(
                doctor.getId(),
                MONDAY,
                MONDAY
        );

        assertThat(response.doctorId()).isEqualTo(doctor.getId());
        assertThat(response.serviceId()).isNull();
        assertThat(response.days()).singleElement().satisfies(day ->
                assertThat(day.sessions().getFirst().availableSlotCount()).isEqualTo(2)
        );
        verifyNoInteractions(dentalServiceRepository, doctorServiceRepository);
    }

    private List<DoctorSchedule> defaultSchedules(Doctor scheduleDoctor) {
        List<DoctorSchedule> schedules = new ArrayList<>();
        for (short day = 1; day <= 6; day++) {
            schedules.add(schedule(scheduleDoctor, day, ScheduleSession.MORNING, "08:00", "11:00"));
            schedules.add(schedule(scheduleDoctor, day, ScheduleSession.AFTERNOON, "14:00", "17:00"));
        }
        return schedules;
    }

    private List<AppointmentStatus> activeStatuses() {
        return List.of(
                AppointmentStatus.BOOKED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.IN_PROGRESS
        );
    }

    private AppointmentQueueOccupancy occupancy(
            LocalDate date,
            ScheduleSession session,
            int queueNumber
    ) {
        LocalTime startTime = switch (session) {
            case MORNING -> queueNumber == 1 ? LocalTime.of(8, 0) : LocalTime.of(9, 30);
            case AFTERNOON -> queueNumber == 1 ? LocalTime.of(14, 0) : LocalTime.of(15, 30);
        };
        AppointmentQueueOccupancy occupancy = mock(AppointmentQueueOccupancy.class);
        when(occupancy.getAppointmentDate()).thenReturn(date);
        when(occupancy.getSession()).thenReturn(session);
        when(occupancy.getQueueNumber()).thenReturn((short) queueNumber);
        when(occupancy.getScheduledStartAt()).thenReturn(toInstant(date, startTime));
        return occupancy;
    }

    private DoctorSchedule schedule(
            Doctor scheduleDoctor,
            short day,
            ScheduleSession session,
            String start,
            String end
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDoctor(scheduleDoctor);
        schedule.setDayOfWeek(day);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(start));
        schedule.setEndTime(LocalTime.parse(end));
        return schedule;
    }

    private Doctor doctor() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("doctor@example.com");
        user.setPasswordHash("bcrypt-hash");
        user.setFullName("Bác sĩ Calendar");
        user.setRole(Role.DOCTOR);
        user.setStatus(UserStatus.ACTIVE);

        Doctor result = new Doctor();
        result.setId(UUID.randomUUID());
        result.setUser(user);
        result.setSpecialization("Nha khoa tổng quát");
        result.setLicenseNumber("CALENDAR-001");
        result.setRating(new BigDecimal("4.5"));
        result.setExperienceYears(8);
        result.setWorkingStatus(DoctorStatus.ACTIVE);
        return result;
    }

    private DentalService dentalService() {
        DentalService result = new DentalService();
        result.setId(UUID.randomUUID());
        result.setName("Khám tổng quát");
        result.setPrice(new BigDecimal("200000"));
        result.setActive(true);
        return result;
    }

    private Instant toInstant(LocalDate date, LocalTime time) {
        return date.atTime(time).atZone(CLINIC_ZONE).toInstant();
    }
}
