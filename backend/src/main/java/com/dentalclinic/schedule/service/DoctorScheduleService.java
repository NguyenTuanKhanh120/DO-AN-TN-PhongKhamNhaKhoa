package com.dentalclinic.schedule.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.schedule.dto.DoctorScheduleRequest;
import com.dentalclinic.schedule.dto.DoctorScheduleResponse;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.repository.DoctorScheduleRepository;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
public class DoctorScheduleService {

    private final DoctorRepository doctorRepository;
    private final DoctorScheduleRepository doctorScheduleRepository;

    public DoctorScheduleService(
            DoctorRepository doctorRepository,
            DoctorScheduleRepository doctorScheduleRepository
    ) {
        this.doctorRepository = doctorRepository;
        this.doctorScheduleRepository = doctorScheduleRepository;
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

    @Transactional
    public DoctorScheduleResponse createSchedule(
            UUID authenticatedUserId,
            DoctorScheduleRequest request
    ) {
        validateTimeRange(request.startTime(), request.endTime());
        Doctor doctor = findDoctorByUserId(authenticatedUserId);
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
            UUID authenticatedUserId,
            UUID scheduleId,
            DoctorScheduleRequest request
    ) {
        validateTimeRange(request.startTime(), request.endTime());
        DoctorSchedule schedule = findOwnedSchedule(scheduleId, authenticatedUserId);
        short dayOfWeek = (short) request.dayOfWeek();
        if (doctorScheduleRepository.existsByDoctor_IdAndDayOfWeekAndSessionAndIdNot(
                schedule.getDoctor().getId(),
                dayOfWeek,
                request.session(),
                scheduleId
        )) {
            throw new BusinessException(ErrorCode.SCHEDULE_ALREADY_EXISTS);
        }

        applyRequest(schedule, request);
        saveSchedule(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID authenticatedUserId, UUID scheduleId) {
        DoctorSchedule schedule = findOwnedSchedule(scheduleId, authenticatedUserId);
        doctorScheduleRepository.delete(schedule);
        doctorScheduleRepository.flush();
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

    private DoctorSchedule findOwnedSchedule(UUID scheduleId, UUID userId) {
        return doctorScheduleRepository.findByIdAndDoctor_User_Id(scheduleId, userId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch làm việc"));
    }

    private void validateTimeRange(LocalTime startTime, LocalTime endTime) {
        if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }
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
