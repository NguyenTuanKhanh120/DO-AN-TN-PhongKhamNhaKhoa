package com.dentalclinic.schedule.service;

import com.dentalclinic.appointment.service.AppointmentQueryService;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DoctorScheduleService {

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;
    private final AppointmentQueryService appointmentQueryService;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;

    public DoctorScheduleService(
            DoctorRepository doctorRepository,
            DoctorScheduleRepository doctorScheduleRepository,
            AppointmentQueryService appointmentQueryService,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.slot-duration-minutes:60}") long slotDurationMinutes
    ) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("Cấu hình thời lượng slot không hợp lệ");
        }
        this.doctorRepository = doctorRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
        this.appointmentQueryService = appointmentQueryService;
        this.clinicTimeProvider = clinicTimeProvider;
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> getPublicSchedule(UUID doctorId) {
        doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        return findSchedulesByDoctor(doctorId);
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> getMySchedules(UUID authenticatedUserId) {
        Doctor doctor = findDoctorByUserId(authenticatedUserId);
        return findSchedulesByDoctor(doctor.getId());
    }

    @Transactional(readOnly = true)
    public List<DoctorScheduleResponse> getAdminSchedules(UUID doctorId) {
        findDoctor(doctorId);
        return findSchedulesByDoctor(doctorId);
    }

    @Transactional
    public DoctorScheduleResponse createSchedule(
            UUID doctorId,
            DoctorScheduleRequest request
    ) {
        validateSchedule(request);
        Doctor doctor = findDoctorForUpdate(doctorId);
        short dayOfWeek = (short) request.dayOfWeek();
        if (doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSession(
                doctor.getId(),
                dayOfWeek,
                request.session()
        )) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        applyRequest(schedule, request);
        saveSchedule(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public DoctorScheduleResponse updateSchedule(
            UUID doctorId,
            UUID scheduleId,
            DoctorScheduleRequest request
    ) {
        validateSchedule(request);
        findDoctorForUpdate(doctorId);
        DoctorSchedule schedule = findDoctorSchedule(doctorId, scheduleId);
        short dayOfWeek = (short) request.dayOfWeek();
        if (doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSessionAndIdNot(
                schedule.getDoctor().getId(),
                dayOfWeek,
                request.session(),
                scheduleId
        )) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        ensureUpdateDoesNotAffectAppointments(schedule, request);
        applyRequest(schedule, request);
        saveSchedule(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID doctorId, UUID scheduleId) {
        findDoctorForUpdate(doctorId);
        DoctorSchedule schedule = findDoctorSchedule(doctorId, scheduleId);
        ensureDeleteDoesNotAffectAppointments(schedule);
        doctorScheduleRepository.delete(schedule);
        doctorScheduleRepository.flush();
    }

    public void createDefaultWeeklySchedule(Doctor doctor) {
        if (doctor.getId() == null) {
            throw new IllegalArgumentException("Bác sĩ phải được lưu trước khi tạo lịch mặc định");
        }
        if (doctorScheduleRepository.existsByDoctor_Id(doctor.getId())) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        List<DoctorSchedule> schedules = new ArrayList<>(12);
        for (short dayOfWeek = DoctorScheduleRules.MONDAY;
                dayOfWeek <= DoctorScheduleRules.SATURDAY;
                dayOfWeek++) {
            schedules.add(defaultSchedule(doctor, dayOfWeek, ScheduleSession.MORNING));
            schedules.add(defaultSchedule(doctor, dayOfWeek, ScheduleSession.AFTERNOON));
        }
        try {
            doctorScheduleRepository.saveAllAndFlush(schedules);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }
    }

    private List<DoctorScheduleResponse> findSchedulesByDoctor(UUID doctorId) {
        return doctorScheduleRepository
                .findAllByDoctor_IdOrderByDayOfWeekAscSessionAscStartTimeAsc(doctorId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private Doctor findDoctorByUserId(UUID userId) {
        return doctorRepository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
    }

    private Doctor findDoctor(UUID doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
    }

    private Doctor findDoctorForUpdate(UUID doctorId) {
        return doctorRepository.findForUpdateById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
    }

    private DoctorSchedule findDoctorSchedule(UUID doctorId, UUID scheduleId) {
        return doctorScheduleRepository.findByIdAndDoctor_Id(scheduleId, doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch làm việc"));
    }

    private void validateSchedule(DoctorScheduleRequest request) {
        DoctorScheduleRules.validate(
                request.dayOfWeek(),
                request.session(),
                request.startTime(),
                request.endTime()
        );
    }

    private void applyRequest(DoctorSchedule schedule, DoctorScheduleRequest request) {
        schedule.setDayOfWeek((short) request.dayOfWeek());
        schedule.setSession(request.session());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
    }

    private void saveSchedule(DoctorSchedule schedule) {
        try {
            doctorScheduleRepository.saveAndFlush(schedule);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }
    }

    private DoctorSchedule defaultSchedule(
            Doctor doctor,
            short dayOfWeek,
            ScheduleSession session
    ) {
        DoctorSchedule schedule = new DoctorSchedule();
        schedule.setDoctor(doctor);
        schedule.setDayOfWeek(dayOfWeek);
        schedule.setSession(session);
        schedule.setStartTime(DoctorScheduleRules.defaultStart(session));
        schedule.setEndTime(DoctorScheduleRules.defaultEnd(session));
        return schedule;
    }

    private void ensureUpdateDoesNotAffectAppointments(
            DoctorSchedule current,
            DoctorScheduleRequest replacement
    ) {
        if (current.getDayOfWeek() == replacement.dayOfWeek()
                && current.getSession() == replacement.session()
                && current.getStartTime().equals(replacement.startTime())
                && current.getEndTime().equals(replacement.endTime())) {
            return;
        }

        long affectedCount = activeFutureStarts(current.getDoctor().getId()).stream()
                .filter(start -> isCoveredBy(current, start))
                .filter(start -> !isCoveredBy(replacement, start))
                .count();
        rejectAffectedAppointments(affectedCount);
    }

    private void ensureDeleteDoesNotAffectAppointments(DoctorSchedule schedule) {
        long affectedCount = activeFutureStarts(schedule.getDoctor().getId()).stream()
                .filter(start -> isCoveredBy(schedule, start))
                .count();
        rejectAffectedAppointments(affectedCount);
    }

    private List<Instant> activeFutureStarts(UUID doctorId) {
        return appointmentQueryService.findActiveScheduledStartsForDoctor(
                doctorId,
                clinicTimeProvider.now()
        );
    }

    private boolean isCoveredBy(DoctorSchedule schedule, Instant scheduledStart) {
        return isCoveredBy(
                schedule.getDayOfWeek(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                scheduledStart
        );
    }

    private boolean isCoveredBy(DoctorScheduleRequest schedule, Instant scheduledStart) {
        return isCoveredBy(
                schedule.dayOfWeek(),
                schedule.startTime(),
                schedule.endTime(),
                scheduledStart
        );
    }

    private boolean isCoveredBy(
            int dayOfWeek,
            LocalTime startTime,
            LocalTime endTime,
            Instant scheduledStart
    ) {
        ZonedDateTime clinicStart = clinicTimeProvider.toClinicTime(scheduledStart);
        LocalTime appointmentStart = clinicStart.toLocalTime();
        return clinicStart.getDayOfWeek().getValue() == dayOfWeek
                && !appointmentStart.isBefore(startTime)
                && !appointmentStart.plus(slotDuration).isAfter(endTime);
    }

    private void rejectAffectedAppointments(long affectedCount) {
        if (affectedCount > 0) {
            throw new ConflictException(
                    "Không thể thay đổi lịch tuần vì có " + affectedCount
                            + " lịch hẹn tương lai đang bị ảnh hưởng"
            );
        }
    }

    private DoctorScheduleResponse toResponse(DoctorSchedule schedule) {
        return new DoctorScheduleResponse(
                schedule.getId(),
                schedule.getDoctor().getId(),
                schedule.getDayOfWeek(),
                schedule.getSession(),
                schedule.getStartTime(),
                schedule.getEndTime(),
                schedule.getCreatedAt(),
                schedule.getUpdatedAt()
        );
    }
}
