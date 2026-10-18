package com.dentalclinic.schedule;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorStatus;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.schedule.service.DoctorScheduleService;
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
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DoctorScheduleServiceTest {

    @Mock
    private DoctorRepository doctorRepository;

    @Mock
    private DoctorScheduleRepository doctorScheduleRepository;

    private DoctorScheduleService service;

    @BeforeEach
    void setUp() {
        service = new DoctorScheduleService(doctorRepository, doctorScheduleRepository);
    }

    @Test
    void shouldReturnPublicWeeklyScheduleForActiveDoctorAccount() {
        Doctor doctor = doctor();
        DoctorSchedule morning = schedule(doctor, (short) 1, ScheduleSession.MORNING, "07:00", "12:00");
        when(doctorRepository.findByIdAndUser_Status(doctor.getId(), UserStatus.ACTIVE))
                .thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(List.of(morning));

        List<DoctorScheduleResponse> response = service.getPublicSchedule(doctor.getId());

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.doctorId()).isEqualTo(doctor.getId());
            assertThat(item.dayOfWeek()).isEqualTo(1);
            assertThat(item.session()).isEqualTo(ScheduleSession.MORNING);
            assertThat(item.startTime()).isEqualTo(LocalTime.of(7, 0));
        });
    }

    @Test
    void shouldHideScheduleWhenDoctorAccountIsInactive() {
        UUID doctorId = UUID.randomUUID();
        when(doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPublicSchedule(doctorId))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy bác sĩ");

        verify(doctorScheduleRepository, never())
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctorId);
    }

    @Test
    void shouldReturnOnlyAuthenticatedDoctorSchedules() {
        Doctor doctor = doctor();
        DoctorSchedule afternoon = schedule(
                doctor,
                (short) 2,
                ScheduleSession.AFTERNOON,
                "13:00",
                "18:00"
        );
        when(doctorRepository.findByUserId(doctor.getUser().getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(List.of(afternoon));

        List<DoctorScheduleResponse> response = service.getMySchedules(doctor.getUser().getId());

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(afternoon.getId());
            assertThat(item.doctorId()).isEqualTo(doctor.getId());
        });
    }

    @Test
    void shouldReturnDoctorSchedulesForAdminManagement() {
        Doctor doctor = doctor();
        DoctorSchedule morning = schedule(
                doctor,
                (short) 1,
                ScheduleSession.MORNING,
                "07:00",
                "12:00"
        );
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(
                doctor.getId()
        )).thenReturn(List.of(morning));

        List<DoctorScheduleResponse> response = service.getAdminSchedules(doctor.getId());

        assertThat(response).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(morning.getId());
            assertThat(item.doctorId()).isEqualTo(doctor.getId());
        });
    }

    @Test
    void shouldCreateScheduleForDoctorSelectedByAdmin() {
        Doctor doctor = doctor();
        DoctorScheduleRequest request = request(1, ScheduleSession.MORNING, "07:00", "12:00");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSession(
                doctor.getId(),
                (short) 1,
                ScheduleSession.MORNING
        )).thenReturn(false);
        when(doctorScheduleRepository.saveAndFlush(any(DoctorSchedule.class))).thenAnswer(invocation -> {
            DoctorSchedule saved = invocation.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });

        DoctorScheduleResponse response = service.createSchedule(doctor.getId(), request);

        ArgumentCaptor<DoctorSchedule> captor = ArgumentCaptor.forClass(DoctorSchedule.class);
        verify(doctorScheduleRepository).saveAndFlush(captor.capture());
        DoctorSchedule saved = captor.getValue();
        assertThat(saved.getDoctor()).isSameAs(doctor);
        assertThat(saved.getDayOfWeek()).isEqualTo((short) 1);
        assertThat(saved.getSession()).isEqualTo(ScheduleSession.MORNING);
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(7, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(response.id()).isNotNull();
    }

    @Test
    void shouldRejectDuplicateDoctorDayAndSession() {
        Doctor doctor = doctor();
        DoctorScheduleRequest request = request(1, ScheduleSession.MORNING, "07:00", "12:00");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSession(
                doctor.getId(),
                (short) 1,
                ScheduleSession.MORNING
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createSchedule(doctor.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);

        verify(doctorScheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectInvalidTimeRangeBeforeDatabaseAccess() {
        DoctorScheduleRequest request = request(1, ScheduleSession.MORNING, "12:00", "07:00");

        assertThatThrownBy(() -> service.createSchedule(UUID.randomUUID(), request))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Giờ kết thúc phải sau giờ bắt đầu")
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        verify(doctorRepository, never()).findById(any());
    }

    @Test
    void shouldUpdateScheduleForDoctorSelectedByAdmin() {
        Doctor doctor = doctor();
        DoctorSchedule schedule = schedule(
                doctor,
                (short) 1,
                ScheduleSession.MORNING,
                "07:00",
                "12:00"
        );
        DoctorScheduleRequest request = request(2, ScheduleSession.AFTERNOON, "13:30", "17:30");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findByIdAndDoctor_Id(
                schedule.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(schedule));
        when(doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSessionAndIdNot(
                doctor.getId(),
                (short) 2,
                ScheduleSession.AFTERNOON,
                schedule.getId()
        )).thenReturn(false);

        DoctorScheduleResponse response = service.updateSchedule(
                doctor.getId(),
                schedule.getId(),
                request
        );

        assertThat(schedule.getDayOfWeek()).isEqualTo((short) 2);
        assertThat(schedule.getSession()).isEqualTo(ScheduleSession.AFTERNOON);
        assertThat(schedule.getStartTime()).isEqualTo(LocalTime.of(13, 30));
        assertThat(response.endTime()).isEqualTo(LocalTime.of(17, 30));
        verify(doctorScheduleRepository).saveAndFlush(schedule);
    }

    @Test
    void shouldRejectUpdateThatConflictsWithAnotherSchedule() {
        Doctor doctor = doctor();
        DoctorSchedule schedule = schedule(
                doctor,
                (short) 1,
                ScheduleSession.MORNING,
                "07:00",
                "12:00"
        );
        DoctorScheduleRequest request = request(1, ScheduleSession.AFTERNOON, "13:00", "18:00");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findByIdAndDoctor_Id(
                schedule.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(schedule));
        when(doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSessionAndIdNot(
                doctor.getId(),
                (short) 1,
                ScheduleSession.AFTERNOON,
                schedule.getId()
        )).thenReturn(true);

        assertThatThrownBy(() -> service.updateSchedule(
                doctor.getId(),
                schedule.getId(),
                request
        )).isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);

        verify(doctorScheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotUpdateScheduleBelongingToAnotherDoctor() {
        UUID doctorId = UUID.randomUUID();
        UUID scheduleId = UUID.randomUUID();
        Doctor doctor = doctor();
        doctor.setId(doctorId);
        when(doctorRepository.findById(doctorId)).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findByIdAndDoctor_Id(scheduleId, doctorId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateSchedule(
                doctorId,
                scheduleId,
                request(1, ScheduleSession.MORNING, "07:00", "12:00")
        )).isInstanceOf(NotFoundException.class)
                .hasMessage("Không tìm thấy lịch làm việc");

        verify(doctorScheduleRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldDeleteScheduleForDoctorSelectedByAdmin() {
        Doctor doctor = doctor();
        DoctorSchedule schedule = schedule(
                doctor,
                (short) 1,
                ScheduleSession.MORNING,
                "07:00",
                "12:00"
        );
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.findByIdAndDoctor_Id(
                schedule.getId(),
                doctor.getId()
        )).thenReturn(Optional.of(schedule));

        service.deleteSchedule(doctor.getId(), schedule.getId());

        verify(doctorScheduleRepository).delete(schedule);
        verify(doctorScheduleRepository).flush();
    }

    @Test
    void shouldMapDatabaseUniqueConflictToScheduleConflict() {
        Doctor doctor = doctor();
        DoctorScheduleRequest request = request(1, ScheduleSession.MORNING, "07:00", "12:00");
        when(doctorRepository.findById(doctor.getId())).thenReturn(Optional.of(doctor));
        when(doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSession(
                doctor.getId(),
                (short) 1,
                ScheduleSession.MORNING
        )).thenReturn(false);
        when(doctorScheduleRepository.saveAndFlush(any(DoctorSchedule.class)))
                .thenThrow(new DataIntegrityViolationException("unique conflict"));

        assertThatThrownBy(() -> service.createSchedule(doctor.getId(), request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.SCHEDULE_ALREADY_EXISTS);
    }

    private DoctorScheduleRequest request(
            int dayOfWeek,
            ScheduleSession session,
            String startTime,
            String endTime
    ) {
        return new DoctorScheduleRequest(
                dayOfWeek,
                session,
                LocalTime.parse(startTime),
                LocalTime.parse(endTime)
        );
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
        return doctor;
    }

    private DoctorSchedule schedule(
            Doctor doctor,
            short dayOfWeek,
            ScheduleSession session,
            String startTime,
            String endTime
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setId(UUID.randomUUID());
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setSession(session);
        schedule.setStartTime(LocalTime.parse(startTime));
        schedule.setEndTime(LocalTime.parse(endTime));
        schedule.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        schedule.setUpdatedAt(Instant.parse("2026-01-02T00:00:00Z"));
        return schedule;
    }
}
