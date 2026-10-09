package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.StartExaminationResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AppointmentStartExaminationService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentStartExaminationService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional
    public StartExaminationResponse start(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findForUpdateByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        if (appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new ConflictException(
                    "Chỉ có thể bắt đầu khám cho lịch hẹn đang ở trạng thái CHECKED_IN"
            );
        }
        if (appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )) {
            throw new ConflictException(
                    "Bác sĩ đang có một ca khám khác chưa hoàn thành"
            );
        }

        Instant actualStartAt = clinicTimeProvider.now();
        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setActualStartAt(actualStartAt);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.START_EXAM);
        history.setChangedBy(doctor.getUser());
        history.setDetails(startDetails(appointment, actualStartAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new StartExaminationResponse(
                appointment.getId(),
                appointment.getStatus(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffsetOrNull(appointment.getCheckedInAt()),
                toClinicOffset(actualStartAt)
        );
    }

    private ObjectNode startDetails(Appointment appointment, Instant actualStartAt) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.CHECKED_IN.name());
        details.put("new_status", AppointmentStatus.IN_PROGRESS.name());
        details.put(
                "scheduled_start_at",
                toClinicOffset(appointment.getScheduledStartAt()).toString()
        );
        OffsetDateTime checkedInAt = toClinicOffsetOrNull(appointment.getCheckedInAt());
        if (checkedInAt == null) {
            details.putNull("checked_in_at");
        } else {
            details.put("checked_in_at", checkedInAt.toString());
        }
        details.put("actual_start_at", toClinicOffset(actualStartAt).toString());
        return details;
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private OffsetDateTime toClinicOffsetOrNull(Instant instant) {
        return instant == null ? null : toClinicOffset(instant);
    }
}
