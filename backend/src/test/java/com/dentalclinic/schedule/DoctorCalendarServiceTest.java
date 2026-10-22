package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.AppointmentStatus;
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
import com.dentalclinic.schedule.entity.DoctorDayOff;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorDayOffRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorCalendarServiceTest {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDate MONDAY = LocalDate.of(2026, 10, 19);
    private static final Instant FIXED_NOW = Instant.parse("2026-10-19T00:00:00Z");

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DentalServiceRepository dentalServiceRepository;

    @Mock
    private DoctorServiceRepository doctorServiceRepository;

    @Mock
    private DoctorScheduleRepository doctorScheduleRepository;

    @Mock
    private DoctorDayOffRepository doctorDayOffRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    private SlotService slotService;
    private Doctor doctor;
    private DentalService dentalService;

    @BeforeEach
    void setUp() {
        slotService = new SlotService(
                doctorRepository,
                dentalServiceRepository,
                doctorServiceRepository,
                doctorScheduleRepository,
                doctorDayOffRepository,
                appointmentRepository,
                new ClinicTimeProvider(Clock.fixed(FIXED_NOW, CLINIC_ZONE)),
                60,
                120
        );
        doctor = doctor();
        dentalService = dentalService();
    }

    @Test
    void shouldBuildRollingThirtyDayCalendarWithThreeRangeQueries() {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(dentalServiceRepository.findById(dentalService.getId()))
                .thenReturn(Optional.of(dentalService));
        when(doctorServiceRepository.findById(mapping.getId())).thenReturn(Optional.of(mapping));
        List<DoctorSchedule> schedules = defaultSchedules(doctor);
        DoctorDayOff morningOff = new DoctorDayOff();
        morningOff.setDoctor(doctor);
        morningOff.setOffDate(MONDAY.plusDays(1));
        morningOff.setSession(ScheduleSession.MORNING);
        DoctorDayOff fullDayMorningOff = new DoctorDayOff();
        fullDayMorningOff.setDoctor(doctor);
        fullDayMorningOff.setOffDate(MONDAY.plusDays(2));
        fullDayMorningOff.setSession(ScheduleSession.MORNING);
        DoctorDayOff fullDayAfternoonOff = new DoctorDayOff();
        fullDayAfternoonOff.setDoctor(doctor);
        fullDayAfternoonOff.setOffDate(MONDAY.plusDays(2));
        fullDayAfternoonOff.setSession(ScheduleSession.AFTERNOON);
        Instant occupiedAfternoonStart = toInstant(MONDAY, LocalTime.of(13, 0));
        LocalDate fullyBookedDate = MONDAY.plusDays(3);
        List<Instant> occupiedStarts = new ArrayList<>();
        occupiedStarts.add(occupiedAfternoonStart);
        List.of(8, 9, 10, 13, 14, 15, 16).forEach(hour ->
                occupiedStarts.add(toInstant(fullyBookedDate, LocalTime.of(hour, 0)))
        );

        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(schedules);
        when(doctorDayOffRepository.findForAvailability(
                doctor.getId(),
                MONDAY,
                MONDAY.plusDays(29)
        )).thenReturn(List.of(morningOff, fullDayMorningOff, fullDayAfternoonOff));
        when(appointmentRepository.findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(30), LocalTime.MIN),
                AppointmentStatus.CANCELLED
        )).thenReturn(occupiedStarts);

        DoctorCalendarResponse response = slotService.getCalendar(
                doctor.getId(),
                dentalService.getId(),
                null,
                null
        );

        assertThat(response.fromDate()).isEqualTo(MONDAY);
        assertThat(response.toDate()).isEqualTo(MONDAY.plusDays(29));
        assertThat(response.days()).hasSize(30);
        assertThat(response.days().getFirst().sessions()).satisfiesExactly(
                morning -> {
                    assertThat(morning.session()).isEqualTo(ScheduleSession.MORNING);
                    assertThat(morning.status()).isEqualTo(CalendarAvailabilityStatus.AVAILABLE);
                    assertThat(morning.availableSlotCount()).isEqualTo(2);
                    assertThat(morning.availableSlots()).extracting(slot -> slot.startAt().toLocalTime())
                            .containsExactly(LocalTime.of(9, 0), LocalTime.of(10, 0));
                },
                afternoon -> {
                    assertThat(afternoon.session()).isEqualTo(ScheduleSession.AFTERNOON);
                    assertThat(afternoon.availableSlotCount()).isEqualTo(3);
                    assertThat(afternoon.availableSlots()).extracting(slot -> slot.startAt().toLocalTime())
                            .containsExactly(
                                    LocalTime.of(14, 0),
                                    LocalTime.of(15, 0),
                                    LocalTime.of(16, 0)
                            );
                }
        );
        assertThat(response.days().get(1).sessions().getFirst().status())
                .isEqualTo(CalendarAvailabilityStatus.DAY_OFF);
        assertThat(response.days().get(2).status()).isEqualTo(CalendarAvailabilityStatus.DAY_OFF);
        assertThat(response.days().get(2).sessions()).allSatisfy(session -> {
            assertThat(session.status()).isEqualTo(CalendarAvailabilityStatus.DAY_OFF);
            assertThat(session.availableSlots()).isEmpty();
        });
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
        verify(doctorDayOffRepository, times(1)).findForAvailability(
                doctor.getId(),
                MONDAY,
                MONDAY.plusDays(29)
        );
        verify(appointmentRepository, times(1)).findOccupiedStartTimes(
                doctor.getId(),
                toInstant(MONDAY, LocalTime.MIN),
                toInstant(MONDAY.plusDays(30), LocalTime.MIN),
                AppointmentStatus.CANCELLED
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

    private List<DoctorSchedule> defaultSchedules(Doctor scheduleDoctor) {
        List<DoctorSchedule> schedules = new ArrayList<>();
        for (short day = 1; day <= 6; day++) {
            schedules.add(schedule(scheduleDoctor, day, ScheduleSession.MORNING, "08:00", "11:00"));
            schedules.add(schedule(scheduleDoctor, day, ScheduleSession.AFTERNOON, "13:00", "17:00"));
        }
        return schedules;
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
