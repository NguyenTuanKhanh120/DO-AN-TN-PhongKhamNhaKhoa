package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.NoShowAppointmentResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AppointmentNoShowService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration noShowGrace;

    public AppointmentNoShowService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.no-show-grace-minutes:15}")
            long noShowGraceMinutes
    ) {
        if (noShowGraceMinutes < 0) {
            throw new IllegalArgumentException("Cấu hình thời gian chờ NO_SHOW không hợp lệ");
        }
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.clinicTimeProvider = clinicTimeProvider;
        this.noShowGrace = Duration.ofMinutes(noShowGraceMinutes);
    }

    @Transactional
    public NoShowAppointmentResponse markNoShow(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        requireEligibleStatus(appointment);
        Instant noShowAt = clinicTimeProvider.now();
        Instant earliestNoShowAt = appointment.getScheduledStartAt().plus(noShowGrace);
        if (noShowAt.isBefore(earliestNoShowAt)) {
            throw new ConflictException(
                    "Chỉ có thể xác nhận NO_SHOW sau giờ hẹn ít nhất "
                            + noShowGrace.toMinutes() + " phút"
            );
        }

        appointment.setStatus(AppointmentStatus.NO_SHOW);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.NO_SHOW);
        history.setChangedBy(doctor.getUser());
        history.setDetails(noShowDetails(appointment, noShowAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new NoShowAppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffset(noShowAt)
        );
    }

    private void requireEligibleStatus(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.CHECKED_IN
                || appointment.getCheckedInAt() != null) {
            throw new ConflictException(
                    "Không thể đánh dấu NO_SHOW vì bệnh nhân đã check-in"
            );
        }
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException(
                    "Chỉ có thể đánh dấu NO_SHOW cho lịch hẹn đang ở trạng thái BOOKED"
            );
        }
    }

    private ObjectNode noShowDetails(Appointment appointment, Instant noShowAt) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.BOOKED.name());
        details.put("new_status", AppointmentStatus.NO_SHOW.name());
        details.put(
                "scheduled_start_at",
                toClinicOffset(appointment.getScheduledStartAt()).toString()
        );
        details.put("no_show_at", toClinicOffset(noShowAt).toString());
        return details;
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }
}
