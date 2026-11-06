package com.dentalclinic.schedule.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.schedule.entity.DoctorSchedule;
import com.dentalclinic.schedule.entity.ScheduleSession;

import java.time.LocalTime;

final class DoctorScheduleRules {

    private DoctorScheduleRules() {
    }

    static void validate(
            int dayOfWeek,
            ScheduleSession session,
            LocalTime startTime,
            LocalTime endTime,
            SchedulingPolicy schedulingPolicy
    ) {
        if (dayOfWeek < SchedulingPolicy.MONDAY || dayOfWeek > SchedulingPolicy.SATURDAY) {
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

        LocalTime requiredStart = schedulingPolicy.sessionStart(session);
        LocalTime requiredEnd = schedulingPolicy.sessionEnd(session);
        if (!startTime.equals(requiredStart) || !endTime.equals(requiredEnd)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    session == ScheduleSession.MORNING
                            ? "Ca MORNING phải đúng khung 08:00–11:00"
                            : "Ca AFTERNOON phải đúng khung 14:00–17:00"
            );
        }
    }

    static boolean isSupported(
            DoctorSchedule schedule,
            SchedulingPolicy schedulingPolicy
    ) {
        try {
            validate(
                    schedule.getDayOfWeek(),
                    schedule.getSession(),
                    schedule.getStartTime(),
                    schedule.getEndTime(),
                    schedulingPolicy
            );
            return true;
        } catch (BusinessException exception) {
            return false;
        }
    }
}
