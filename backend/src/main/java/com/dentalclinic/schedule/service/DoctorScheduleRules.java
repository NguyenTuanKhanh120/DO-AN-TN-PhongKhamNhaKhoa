package com.dentalclinic.schedule.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.Duration;
import java.time.LocalTime;

final class DoctorScheduleRules {

    static final int MONDAY = 1;
    static final int SATURDAY = 6;
    static final LocalTime MORNING_START = LocalTime.of(8, 0);
    static final LocalTime MORNING_END = LocalTime.of(11, 0);
    static final LocalTime AFTERNOON_START = LocalTime.of(13, 0);
    static final LocalTime AFTERNOON_END = LocalTime.of(17, 0);

    private static final long SLOT_MINUTES = 60;

    private DoctorScheduleRules() {
    }

    static void validate(
            int dayOfWeek,
            ScheduleSession session,
            LocalTime startTime,
            LocalTime endTime
    ) {
        if (dayOfWeek < MONDAY || dayOfWeek > SATURDAY) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Lịch làm việc chỉ áp dụng từ Thứ Hai đến Thứ Bảy"
            );
        }
        if (session == null || startTime == null || endTime == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Thông tin lịch làm việc chưa đầy đủ");
        }
        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Giờ kết thúc phải sau giờ bắt đầu"
            );
        }
        if (!isWholeHour(startTime)
                || !isWholeHour(endTime)
                || Duration.between(startTime, endTime).toMinutes() % SLOT_MINUTES != 0) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Giờ làm việc phải khớp ranh giới slot 60 phút"
            );
        }

        LocalTime allowedStart = defaultStart(session);
        LocalTime allowedEnd = defaultEnd(session);
        if (startTime.isBefore(allowedStart) || endTime.isAfter(allowedEnd)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    session == ScheduleSession.MORNING
                            ? "Ca MORNING phải nằm trong khung 08:00–11:00"
                            : "Ca AFTERNOON phải nằm trong khung 13:00–17:00"
            );
        }
    }

    static boolean isSupported(DoctorSchedule schedule) {
        try {
            validate(
                    schedule.getDayOfWeek(),
                    schedule.getSession(),
                    schedule.getStartTime(),
                    schedule.getEndTime()
            );
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }

    static LocalTime defaultStart(ScheduleSession session) {
        return session == ScheduleSession.MORNING ? MORNING_START : AFTERNOON_START;
    }

    static LocalTime defaultEnd(ScheduleSession session) {
        return session == ScheduleSession.MORNING ? MORNING_END : AFTERNOON_END;
    }

    private static boolean isWholeHour(LocalTime time) {
        return time.getMinute() == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }
}
