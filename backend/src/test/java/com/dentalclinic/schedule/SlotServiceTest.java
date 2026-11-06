package com.dentalclinic.schedule;

import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentQueueOccupancy;
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
                new SchedulingPolicy(timeProvider)
        );
    }

    @Test
    void shouldGenerateCasesAndExcludeOccupiedQueue() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(),
                (short) 1
        )).thenReturn(List.of(
                schedule(doctor, ScheduleSession.MORNING, "08:00", "11:00"),
                schedule(doctor, ScheduleSession.AFTERNOON, "14:00", "17:00")
        ));
        AppointmentQueueOccupancy occupiedCase = occupancy(
                NEXT_MONDAY,
                ScheduleSession.AFTERNOON,
                (short) 1
        );
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                NEXT_MONDAY,
                NEXT_MONDAY,
                activeStatuses()
        )).thenReturn(List.of(occupiedCase));

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                NEXT_MONDAY
        );

        assertThat(slots).extracting(AvailableSlotResponse::estimatedStartTime)
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 30),
                        LocalTime.of(15, 30)
                );
        assertThat(slots.getLast().session()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(slots.getLast().queueNumber()).isEqualTo(2);
        assertThat(slots.getLast().estimatedStartAt().toString())
                .isEqualTo("2026-10-19T15:30+07:00");
    }

    @Test
    void shouldGenerateTwoMorningAndTwoAfternoonCasesForStandardSchedule() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(
                schedule(doctor, ScheduleSession.MORNING, "08:00", "11:00"),
                schedule(doctor, ScheduleSession.AFTERNOON, "14:00", "17:00")
        ));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                NEXT_MONDAY,
                NEXT_MONDAY,
                activeStatuses()
        )).thenReturn(List.of());

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(), dentalService.getId(), NEXT_MONDAY
        );

        assertThat(slots).hasSize(4);
        assertThat(slots).filteredOn(slot -> slot.session() == ScheduleSession.MORNING)
                .hasSize(2);
        assertThat(slots).filteredOn(slot -> slot.session() == ScheduleSession.AFTERNOON)
                .hasSize(2);
        assertThat(slots).extracting(AvailableSlotResponse::estimatedStartTime)
                .containsExactly(
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 30),
                        LocalTime.of(14, 0),
                        LocalTime.of(15, 30)
                );
        assertThat(slots).extracting(AvailableSlotResponse::queueNumber)
                .containsExactly(1, 2, 1, 2);
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
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                followingMonday,
                followingMonday,
                activeStatuses()
        )).thenReturn(List.of());

        List<AvailableSlotResponse> slots = slotService.getAvailableSlots(
                doctor.getId(), dentalService.getId(), followingMonday
        );

        assertThat(slots).hasSize(2);
        assertThat(slots).allMatch(slot -> slot.session() == ScheduleSession.MORNING);
    }

    @Test
    void shouldReturnNoCasesForCurrentDateAtOrAfterDailyCutoff() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor,
                ScheduleSession.MORNING,
                "08:00",
                "11:00"
        )));

        assertThat(slotService.getAvailableSlots(
                doctor.getId(),
                dentalService.getId(),
                MONDAY
        )).isEmpty();

        verify(appointmentRepository, never()).findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        );
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

        verify(appointmentRepository, never()).findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
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
    void shouldAllowBookingCaseAt0459WhenQueueIsAvailable() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        DoctorService mapping = stubValidDoctorAndService(doctor, dentalService);
        SlotService beforeCutoffService = slotServiceAt(Instant.parse("2026-10-11T21:59:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.AFTERNOON, "14:00", "17:00"
        )));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        )).thenReturn(List.of());

        DoctorService result = beforeCutoffService.requireAvailableCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                1
        );

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void shouldRejectBookingCaseAt0500WithoutCheckingOccupancy() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        SlotService atCutoffService = slotServiceAt(Instant.parse("2026-10-11T22:00:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.AFTERNOON, "14:00", "17:00"
        )));

        assertThatThrownBy(() -> atCutoffService.requireAvailableCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                1
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Lịch khám trong ngày phải được đặt trước 05:00");

        verify(appointmentRepository, never()).findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        );
    }

    @Test
    void shouldRejectOccupiedBookingCase() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        SlotService beforeCutoffService = slotServiceAt(Instant.parse("2026-10-11T21:59:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.AFTERNOON, "14:00", "17:00"
        )));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        )).thenReturn(List.of(occupancy(
                MONDAY,
                ScheduleSession.AFTERNOON,
                (short) 1
        )));

        assertThatThrownBy(() -> beforeCutoffService.requireAvailableCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                1
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác");
    }

    @Test
    void shouldIgnoreCurrentAppointmentWhenCheckingRescheduleCase() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        DoctorService mapping = stubValidDoctorAndService(doctor, dentalService);
        UUID currentAppointmentId = UUID.randomUUID();
        SlotService beforeCutoffService = slotServiceAt(Instant.parse("2026-10-11T21:59:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.AFTERNOON, "14:00", "17:00"
        )));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        )).thenReturn(List.of(occupancy(
                currentAppointmentId,
                MONDAY,
                ScheduleSession.AFTERNOON,
                (short) 2
        )));

        DoctorService result = beforeCutoffService.requireAvailableRescheduleCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                2,
                currentAppointmentId
        );

        assertThat(result).isSameAs(mapping);
    }

    @Test
    void shouldRejectRescheduleCaseOccupiedByAnotherAppointment() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        UUID currentAppointmentId = UUID.randomUUID();
        SlotService beforeCutoffService = slotServiceAt(Instant.parse("2026-10-11T21:59:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.AFTERNOON, "14:00", "17:00"
        )));
        when(appointmentRepository.findActiveQueueOccupancies(
                doctor.getId(),
                MONDAY,
                MONDAY,
                activeStatuses()
        )).thenReturn(List.of(occupancy(
                UUID.randomUUID(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                (short) 2
        )));

        assertThatThrownBy(() -> beforeCutoffService.requireAvailableRescheduleCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.AFTERNOON,
                2,
                currentAppointmentId
        )).isInstanceOf(ConflictException.class)
                .hasMessage("Khung giờ mới vừa được người khác đặt. "
                        + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.");
    }

    @Test
    void shouldRejectRescheduleCaseAtDailyCutoff() {
        Doctor doctor = doctor();
        DentalService dentalService = dentalService(true);
        stubValidDoctorAndService(doctor, dentalService);
        SlotService atCutoffService = slotServiceAt(Instant.parse("2026-10-11T22:00:00Z"));
        when(doctorScheduleRepository.findAllByDoctor_IdAndDayOfWeekOrderByStartTimeAsc(
                doctor.getId(), (short) 1
        )).thenReturn(List.of(schedule(
                doctor, ScheduleSession.MORNING, "08:00", "11:00"
        )));

        assertThatThrownBy(() -> atCutoffService.requireAvailableRescheduleCase(
                doctor.getId(),
                dentalService.getId(),
                MONDAY,
                ScheduleSession.MORNING,
                1,
                UUID.randomUUID()
        )).isInstanceOf(BusinessException.class)
                .hasMessage("Không thể đổi sang ngày đã qua thời hạn 05:00");
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
            Short queueNumber
    ) {
        return occupancy(null, date, session, queueNumber);
    }

    private AppointmentQueueOccupancy occupancy(
            UUID appointmentId,
            LocalDate date,
            ScheduleSession session,
            Short queueNumber
    ) {
        return new AppointmentQueueOccupancy() {
            @Override
            public UUID getAppointmentId() {
                return appointmentId;
            }

            @Override
            public LocalDate getAppointmentDate() {
                return date;
            }

            @Override
            public ScheduleSession getSession() {
                return session;
            }

            @Override
            public Short getQueueNumber() {
                return queueNumber;
            }
        };
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
