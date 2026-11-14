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
import com.dentalclinic.payment.service.PaymentService;
import com.dentalclinic.schedule.service.SchedulingPolicy;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentStartExaminationService {

    private static final List<AppointmentStatus> ACTIVE_QUEUE_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_PROGRESS
    );

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final PaymentService paymentService;
    private final SchedulingPolicy schedulingPolicy;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentStartExaminationService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            PaymentService paymentService,
            SchedulingPolicy schedulingPolicy,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.paymentService = paymentService;
        this.schedulingPolicy = schedulingPolicy;
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

        AppointmentStatus oldStatus = appointment.getStatus();
        if (oldStatus != AppointmentStatus.BOOKED
                && oldStatus != AppointmentStatus.CHECKED_IN) {
            throw new ConflictException(
                    "Chỉ có thể bắt đầu khám cho lịch hẹn đang ở trạng thái BOOKED"
            );
        }
        Instant actualStartAt = clinicTimeProvider.now();
        requireQueueReady(doctor, appointment, actualStartAt);
        if (appointmentRepository.existsOtherByDoctorIdAndStatus(
                doctor.getId(),
                AppointmentStatus.IN_PROGRESS,
                appointment.getId()
        )) {
            throw new ConflictException(
                    "Bác sĩ đang có một ca khám khác chưa hoàn thành"
            );
        }

        appointment.setStatus(AppointmentStatus.IN_PROGRESS);
        appointment.setActualStartAt(actualStartAt);
        appointmentRepository.saveAndFlush(appointment);
        paymentService.initializeDraftForExamination(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.START_EXAM);
        history.setChangedBy(doctor.getUser());
        history.setDetails(startDetails(appointment, oldStatus, actualStartAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new StartExaminationResponse(
                appointment.getId(),
                appointment.getStatus(),
                appointment.getAppointmentDate(),
                appointment.getSession(),
                appointment.getQueueNumber().intValue(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffsetOrNull(appointment.getCheckedInAt()),
                toClinicOffset(actualStartAt)
        );
    }

    private void requireQueueReady(
            Doctor doctor,
            Appointment appointment,
            Instant actualStartAt
    ) {
        Instant sessionStartAt = clinicTimeProvider.toInstant(
                appointment.getAppointmentDate(),
                schedulingPolicy.sessionStart(appointment.getSession())
        );
        if (actualStartAt.isBefore(sessionStartAt)) {
            throw new ConflictException(
                    "Chỉ có thể bắt đầu khám sau khi buổi khám bắt đầu lúc "
                            + schedulingPolicy.sessionStart(appointment.getSession())
            );
        }

        if (appointment.getQueueNumber() == SchedulingPolicy.MAX_QUEUE_NUMBER
                && appointmentRepository.existsActiveDoctorQueue(
                doctor.getId(),
                appointment.getAppointmentDate(),
                appointment.getSession(),
                (short) SchedulingPolicy.MIN_QUEUE_NUMBER,
                ACTIVE_QUEUE_STATUSES
        )) {
            throw new ConflictException(
                    "Ca 2 chỉ có thể bắt đầu khi Ca 1 đã hoàn thành, bị hủy, "
                            + "không đến hoặc không tồn tại"
            );
        }
    }

    private ObjectNode startDetails(
            Appointment appointment,
            AppointmentStatus oldStatus,
            Instant actualStartAt
    ) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", oldStatus.name());
        details.put("new_status", AppointmentStatus.IN_PROGRESS.name());
        details.put(
                "estimated_start_at",
                toClinicOffset(appointment.getScheduledStartAt()).toString()
        );
        details.put("appointment_date", appointment.getAppointmentDate().toString());
        details.put("session", appointment.getSession().name());
        details.put("queue_number", appointment.getQueueNumber());
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
