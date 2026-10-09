package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.RescheduleAppointmentRequest;
import com.dentalclinic.appointment.dto.RescheduleAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.common.util.PostgreSqlErrorUtils;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.service.SlotService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.UUID;

@Service
public class AppointmentRescheduleService {

    private static final String CUTOFF_MESSAGE = "Không thể đổi lịch vì lịch hẹn còn dưới "
            + "1 giờ 30 phút. Vui lòng giữ lịch hiện tại hoặc liên hệ phòng khám.";
    private static final String SLOT_CONFLICT_MESSAGE = "Khung giờ mới vừa được người khác đặt. "
            + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.";

    private final PatientRepository patientRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final SlotService slotService;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration rescheduleCutoff;
    private final Duration slotDuration;

    public AppointmentRescheduleService(
            PatientRepository patientRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            SlotService slotService,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.reschedule-cutoff-minutes:90}")
            long rescheduleCutoffMinutes,
            @Value("${clinic.appointment.slot-duration-minutes:60}")
            long slotDurationMinutes
    ) {
        if (rescheduleCutoffMinutes < 0 || slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("Cấu hình đổi lịch hoặc thời lượng slot không hợp lệ");
        }
        this.patientRepository = patientRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.slotService = slotService;
        this.clinicTimeProvider = clinicTimeProvider;
        this.rescheduleCutoff = Duration.ofMinutes(rescheduleCutoffMinutes);
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
    }

    @Transactional
    public RescheduleAppointmentResponse reschedule(
            UUID authenticatedUserId,
            UUID appointmentId,
            RescheduleAppointmentRequest request
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        requireBooked(appointment);
        Instant changedAt = clinicTimeProvider.now();
        requireBeforeCutoff(appointment, changedAt);

        Instant oldScheduledStartAt = appointment.getScheduledStartAt();
        Instant newScheduledStartAt = request.scheduledStartAt().toInstant();
        if (newScheduledStartAt.equals(oldScheduledStartAt)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khung giờ mới phải khác khung giờ hiện tại"
            );
        }

        DoctorService doctorService = appointment.getDoctorService();
        UUID doctorId = doctorService.getDoctor().getId();
        UUID serviceId = doctorService.getService().getId();
        slotService.requireAvailableRescheduleSlot(
                doctorId,
                serviceId,
                newScheduledStartAt
        );

        appointment.setScheduledStartAt(newScheduledStartAt);
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            if (PostgreSqlErrorUtils.isUniqueViolation(exception)) {
                throw slotConflict();
            }
            throw exception;
        }

        String reason = normalizeReason(request.reason());
        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.RESCHEDULE);
        history.setChangedBy(patient.getUser());
        history.setReason(reason);
        history.setDetails(rescheduleDetails(
                oldScheduledStartAt,
                newScheduledStartAt,
                changedAt
        ));
        appointmentHistoryRepository.saveAndFlush(history);

        OffsetDateTime oldStart = toClinicOffset(oldScheduledStartAt);
        OffsetDateTime newStart = toClinicOffset(newScheduledStartAt);
        return new RescheduleAppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                oldStart,
                newStart,
                newStart.plus(slotDuration),
                reason,
                toClinicOffset(changedAt)
        );
    }

    private void requireBooked(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Chỉ có thể đổi lịch hẹn đang ở trạng thái BOOKED");
        }
    }

    private void requireBeforeCutoff(Appointment appointment, Instant now) {
        if (appointment.getScheduledStartAt().isBefore(now.plus(rescheduleCutoff))) {
            throw new ConflictException(CUTOFF_MESSAGE);
        }
    }

    private ObjectNode rescheduleDetails(
            Instant oldScheduledStartAt,
            Instant newScheduledStartAt,
            Instant changedAt
    ) {
        ZonedDateTime oldClinicTime = clinicTimeProvider.toClinicTime(oldScheduledStartAt);
        ZonedDateTime newClinicTime = clinicTimeProvider.toClinicTime(newScheduledStartAt);
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_date", oldClinicTime.toLocalDate().toString());
        details.put("old_start_time", oldClinicTime.toLocalTime().toString());
        details.put("new_date", newClinicTime.toLocalDate().toString());
        details.put("new_start_time", newClinicTime.toLocalTime().toString());
        details.put("old_scheduled_start_at", oldClinicTime.toOffsetDateTime().toString());
        details.put("new_scheduled_start_at", newClinicTime.toOffsetDateTime().toString());
        details.put("changed_at", toClinicOffset(changedAt).toString());
        return details;
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private String normalizeReason(String reason) {
        return reason.trim().replaceAll("\\s+", " ");
    }

    private ConflictException slotConflict() {
        return new ConflictException(SLOT_CONFLICT_MESSAGE);
    }
}
