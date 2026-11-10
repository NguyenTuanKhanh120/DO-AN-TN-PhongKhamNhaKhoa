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
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.patient.entity.Patient;
import com.dentalclinic.patient.repository.PatientRepository;
import com.dentalclinic.schedule.entity.ScheduleSession;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.dentalclinic.schedule.service.SlotService;
import com.dentalclinic.user.entity.User;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentRescheduleService {

    private static final String CUTOFF_MESSAGE = "Không thể tự đổi lịch từ 05:00 ngày khám. "
            + "Vui lòng giữ lịch hiện tại hoặc liên hệ phòng khám.";
    private static final String SLOT_CONFLICT_MESSAGE = "Khung giờ mới vừa được người khác đặt. "
            + "Lịch hiện tại của bạn vẫn được giữ nguyên. Vui lòng chọn khung giờ khác.";
    private static final List<AppointmentStatus> ACTIVE_APPOINTMENT_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_PROGRESS
    );

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final SlotService slotService;
    private final SchedulingPolicy schedulingPolicy;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentRescheduleService(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            SlotService slotService,
            SchedulingPolicy schedulingPolicy,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.slotService = slotService;
        this.schedulingPolicy = schedulingPolicy;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional
    public RescheduleAppointmentResponse reschedule(
            UUID authenticatedUserId,
            UUID appointmentId,
            RescheduleAppointmentRequest request
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        User patientUser = requireLinkedUser(patient);
        Appointment snapshot = appointmentRepository
                .findDetailedByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        UUID doctorId = snapshot.getDoctorService().getDoctor().getId();
        doctorRepository.findForUpdateById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndPatientId(appointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        requireBooked(appointment);
        Instant changedAt = clinicTimeProvider.now();
        requireBeforeCutoff(appointment);

        Instant oldScheduledStartAt = appointment.getScheduledStartAt();
        LocalDate oldAppointmentDate = appointment.getAppointmentDate();
        ScheduleSession oldSession = appointment.getSession();
        short oldQueueNumber = appointment.getQueueNumber();
        SchedulingPolicy.ScheduledCase newCase = schedulingPolicy.resolveCase(
                request.appointmentDate(),
                request.session(),
                request.queueNumber()
        );
        Instant newScheduledStartAt = newCase.estimatedStartAt();
        if (newScheduledStartAt.equals(oldScheduledStartAt)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khung giờ mới phải khác khung giờ hiện tại"
            );
        }

        DoctorService doctorService = appointment.getDoctorService();
        UUID serviceId = doctorService.getService().getId();
        if (appointmentRepository.existsOtherActivePatientSession(
                appointment.getId(),
                patient.getId(),
                newCase.appointmentDate(),
                newCase.session(),
                ACTIVE_APPOINTMENT_STATUSES
        )) {
            throw new ConflictException(
                    "Bạn đã có một lịch khám đang hoạt động trong buổi muốn đổi tới"
            );
        }
        slotService.requireAvailableRescheduleCase(
                doctorId,
                serviceId,
                newCase.appointmentDate(),
                newCase.session(),
                newCase.queueNumber(),
                appointment.getId()
        );

        appointment.setScheduledStartAt(newScheduledStartAt);
        appointment.setAppointmentDate(newCase.appointmentDate());
        appointment.setSession(newCase.session());
        appointment.setQueueNumber((short) newCase.queueNumber());
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
        history.setChangedBy(patientUser);
        history.setReason(reason);
        history.setDetails(rescheduleDetails(
                oldScheduledStartAt,
                oldAppointmentDate,
                oldSession,
                oldQueueNumber,
                newCase,
                changedAt
        ));
        appointmentHistoryRepository.saveAndFlush(history);

        return new RescheduleAppointmentResponse(
                appointment.getId(),
                appointment.getStatus(),
                oldAppointmentDate,
                oldSession,
                oldQueueNumber,
                toClinicOffset(oldScheduledStartAt),
                newCase.appointmentDate(),
                newCase.session(),
                newCase.queueNumber(),
                schedulingPolicy.sessionStart(newCase.session()),
                schedulingPolicy.sessionEnd(newCase.session()),
                schedulingPolicy.estimatedStartTime(
                        newCase.session(),
                        newCase.queueNumber()
                ),
                toClinicOffset(newScheduledStartAt),
                reason,
                toClinicOffset(changedAt)
        );
    }

    private void requireBooked(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.BOOKED) {
            throw new ConflictException("Chỉ có thể đổi lịch hẹn đang ở trạng thái BOOKED");
        }
    }

    private void requireBeforeCutoff(Appointment appointment) {
        if (!schedulingPolicy.isBeforePatientDailyCutoff(appointment.getAppointmentDate())) {
            throw new ConflictException(CUTOFF_MESSAGE);
        }
    }

    private ObjectNode rescheduleDetails(
            Instant oldScheduledStartAt,
            LocalDate oldAppointmentDate,
            ScheduleSession oldSession,
            short oldQueueNumber,
            SchedulingPolicy.ScheduledCase newCase,
            Instant changedAt
    ) {
        ZonedDateTime oldClinicTime = clinicTimeProvider.toClinicTime(oldScheduledStartAt);
        ZonedDateTime newClinicTime = clinicTimeProvider.toClinicTime(newCase.estimatedStartAt());
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_appointment_date", oldAppointmentDate.toString());
        details.put("old_session", oldSession.name());
        details.put("old_queue_number", oldQueueNumber);
        details.put("new_appointment_date", newCase.appointmentDate().toString());
        details.put("new_session", newCase.session().name());
        details.put("new_queue_number", newCase.queueNumber());
        details.put("old_estimated_start_at", oldClinicTime.toOffsetDateTime().toString());
        details.put("new_estimated_start_at", newClinicTime.toOffsetDateTime().toString());
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

    private User requireLinkedUser(Patient patient) {
        if (patient.getUser() == null) {
            throw new NotFoundException("Không tìm thấy tài khoản liên kết của bệnh nhân");
        }
        return patient.getUser();
    }
}
