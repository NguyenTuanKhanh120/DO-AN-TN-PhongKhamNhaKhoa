package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.CancelAppointmentRequest;
import com.dentalclinic.appointment.dto.CancelAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
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
public class AppointmentCancellationService {

    private static final String CUTOFF_MESSAGE = "Không thể hủy lịch vì lịch hẹn còn dưới "
            + "1 giờ 30 phút. Vui lòng liên hệ phòng khám nếu có trường hợp đặc biệt.";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration cancellationCutoff;

    public AppointmentCancellationService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.cancellation-cutoff-minutes:90}")
            long cancellationCutoffMinutes
    ) {
        if (cancellationCutoffMinutes < 0) {
            throw new IllegalArgumentException("Cấu hình thời hạn hủy lịch không hợp lệ");
        }
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.clinicTimeProvider = clinicTimeProvider;
        this.cancellationCutoff = Duration.ofMinutes(cancellationCutoffMinutes);
    }

    @Transactional
    public CancelAppointmentResponse cancel(
            UUID authenticatedUserId,
            UUID appointmentId,
            CancelAppointmentRequest request
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        requireBooked(appointment);
        Instant cancelledAt = clinicTimeProvider.now();
        requireBeforeCutoff(appointment, cancelledAt);
        String reason = normalizeReason(request.reason());

        appointment.setStatus(AppointmentStatus.CANCELLED);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.CANCEL);
        history.setChangedBy(patient.getUser());
        history.setReason(reason);
        history.setDetails(cancellationDetails(appointment, cancelledAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new CancelAppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                toClinicOffset(appointment.getScheduledStartAt()),
                reason,
                toClinicOffset(cancelledAt)
        );
    }

    private void requireBooked(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Chỉ có thể hủy lịch hẹn đang ở trạng thái BOOKED");
        }
    }

    private void requireBeforeCutoff(Appointment appointment, Instant now) {
        Instant cancellationDeadline = now.plus(cancellationCutoff);
        if (appointment.getScheduledStartAt().isBefore(cancellationDeadline)) {
            throw new ConflictException(CUTOFF_MESSAGE);
        }
    }

    private ObjectNode cancellationDetails(Appointment appointment, Instant cancelledAt) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.BOOKED.name());
        details.put("new_status", AppointmentStatus.CANCELLED.name());
        details.put(
                "scheduled_start_at",
                toClinicOffset(appointment.getScheduledStartAt()).toString()
        );
        details.put("cancelled_at", toClinicOffset(cancelledAt).toString());
        return details;
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private String normalizeReason(String reason) {
        return reason.trim().replaceAll("\\s+", " ");
    }
}
