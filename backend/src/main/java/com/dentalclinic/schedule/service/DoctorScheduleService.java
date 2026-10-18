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
        validateTimeRange(request.startTime(), request.endTime());
        Doctor doctor = findDoctor(doctorId);
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
        validateTimeRange(request.startTime(), request.endTime());
        findDoctor(doctorId);
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

        applyRequest(schedule, request);
        saveSchedule(schedule);
        return toResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(UUID doctorId, UUID scheduleId) {
        findDoctor(doctorId);
        DoctorSchedule schedule = findDoctorSchedule(doctorId, scheduleId);
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

    private Doctor findDoctor(UUID doctorId) {
        return doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
    }

    private DoctorSchedule findDoctorSchedule(UUID doctorId, UUID scheduleId) {
        return doctorScheduleRepository.findByIdAndDoctor_Id(scheduleId, doctorId)
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
