package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.BookAppointmentRequest;
import com.dentalclinic.appointment.dto.BookAppointmentResponse;
import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentAction;
import com.dentalclinic.appointment.entity.AppointmentHistory;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentHistoryRepository;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.common.util.PostgreSqlErrorUtils;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
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
import java.util.List;
import java.util.UUID;

@Service
public class AppointmentBookingService {

    private static final List<AppointmentStatus> ACTIVE_FOLLOW_UP_STATUSES = List.of(
            AppointmentStatus.BOOKED,
            AppointmentStatus.CHECKED_IN,
            AppointmentStatus.IN_PROGRESS
    );

    private final PatientRepository patientRepository;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final SlotService slotService;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;

    public AppointmentBookingService(
            PatientRepository patientRepository,
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            MedicalRecordRepository medicalRecordRepository,
            SlotService slotService,
            ClinicTimeProvider clinicTimeProvider,
            @Value("${clinic.appointment.slot-duration-minutes:60}") long slotDurationMinutes
    ) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("Cấu hình thời lượng slot không hợp lệ");
        }
        this.patientRepository = patientRepository;
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.slotService = slotService;
        this.clinicTimeProvider = clinicTimeProvider;
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
    }

    @Transactional
    public BookAppointmentResponse book(
            UUID authenticatedUserId,
            BookAppointmentRequest request
    ) {
        Patient patient = patientRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bệnh nhân"));
        Appointment previousAppointment = requireEligiblePreviousAppointment(
                patient,
                request.previousAppointmentId()
        );
        doctorRepository.findForUpdateById(request.doctorId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        Instant scheduledStartAt = request.scheduledStartAt().toInstant();
        DoctorService doctorService = slotService.requireAvailableSlot(
                request.doctorId(),
                request.serviceId(),
                scheduledStartAt
        );

        Appointment appointment = new Appointment();
        appointment.setPatient(patient);
        appointment.setDoctorService(doctorService);
        appointment.setPreviousAppointment(previousAppointment);
        appointment.setScheduledStartAt(scheduledStartAt);
        appointment.setStatus(AppointmentStatus.BOOKED);

        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            if (PostgreSqlErrorUtils.isUniqueViolation(exception)) {
                throw previousAppointment == null
                        ? slotConflict()
                        : new ConflictException(
                                "Khung giờ đã được đặt hoặc chỉ định tái khám đã có lịch đang hoạt động"
                        );
            }
            throw exception;
        }

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.BOOK);
        history.setChangedBy(patient.getUser());
        history.setDetails(bookDetails(appointment));
        appointmentHistoryRepository.saveAndFlush(history);

        return toResponse(appointment);
    }

    private ObjectNode bookDetails(Appointment appointment) {
        OffsetDateTime scheduledAt = clinicTimeProvider
                .toClinicTime(appointment.getScheduledStartAt())
                .toOffsetDateTime();
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("status", AppointmentStatus.BOOKED.name());
        details.put(
                "booking_type",
                appointment.getPreviousAppointment() == null ? "STANDARD" : "FOLLOW_UP"
        );
        if (appointment.getPreviousAppointment() != null) {
            details.put(
                    "previous_appointment_id",
                    appointment.getPreviousAppointment().getId().toString()
            );
        }
        details.put("doctor_id", appointment.getDoctorService().getDoctor().getId().toString());
        details.put("service_id", appointment.getDoctorService().getService().getId().toString());
        details.put("scheduled_start_at", scheduledAt.toString());
        return details;
    }

    private BookAppointmentResponse toResponse(Appointment appointment) {
        OffsetDateTime scheduledStart = clinicTimeProvider
                .toClinicTime(appointment.getScheduledStartAt())
                .toOffsetDateTime();
        return new BookAppointmentResponse(
                appointment.getId(),
                appointment.getPatient().getId(),
                appointment.getDoctorService().getDoctor().getId(),
                appointment.getDoctorService().getService().getId(),
                appointment.getPreviousAppointment() == null
                        ? null
                        : appointment.getPreviousAppointment().getId(),
                scheduledStart,
                scheduledStart.plus(slotDuration),
                appointment.getStatus(),
                appointment.getCreatedAt()
        );
    }

    private Appointment requireEligiblePreviousAppointment(
            Patient patient,
            UUID previousAppointmentId
    ) {
        if (previousAppointmentId == null) {
            return null;
        }

        Appointment previousAppointment = appointmentRepository
                .findForUpdateByIdAndPatientId(previousAppointmentId, patient.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch khám trước"));
        if (previousAppointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ConflictException(
                    "Chỉ có thể đặt tái khám từ lịch khám đã hoàn thành"
            );
        }

        MedicalRecord medicalRecord = medicalRecordRepository
                .findByAppointmentId(previousAppointmentId)
                .orElseThrow(() -> new ConflictException(
                        "Lịch khám trước chưa có bệnh án yêu cầu tái khám"
                ));
        if (!medicalRecord.isFollowUpRequired()) {
            throw new ConflictException("Bệnh án không yêu cầu tái khám");
        }
        if (appointmentRepository.existsActiveFollowUp(
                previousAppointmentId,
                ACTIVE_FOLLOW_UP_STATUSES
        )) {
            throw new ConflictException(
                    "Chỉ định tái khám đã có một lịch hẹn đang hoạt động"
            );
        }
        return previousAppointment;
    }

    private ConflictException slotConflict() {
        return new ConflictException(
                "Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác"
        );
    }
}
