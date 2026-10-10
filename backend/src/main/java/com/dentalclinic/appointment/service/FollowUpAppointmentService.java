package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.CreateFollowUpAppointmentRequest;
import com.dentalclinic.appointment.dto.FollowUpAppointmentResponse;
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
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.dentalclinic.schedule.dto.AvailableSlotResponse;
import com.dentalclinic.schedule.service.SlotService;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class FollowUpAppointmentService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final SlotService slotService;
    private final ClinicTimeProvider clinicTimeProvider;
    private final Duration slotDuration;

    public FollowUpAppointmentService(
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
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.slotService = slotService;
        this.clinicTimeProvider = clinicTimeProvider;
        this.slotDuration = Duration.ofMinutes(slotDurationMinutes);
    }

    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> getFollowUpSlots(
            UUID authenticatedUserId,
            UUID previousAppointmentId,
            UUID serviceId,
            LocalDate date
    ) {
        Doctor doctor = findDoctor(authenticatedUserId);
        Appointment previousAppointment = appointmentRepository
                .findDetailedByIdAndDoctorId(previousAppointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        requireFollowUpEligible(previousAppointment);

        return slotService.getAvailableSlots(doctor.getId(), serviceId, date);
    }

    @Transactional
    public FollowUpAppointmentResponse createFollowUp(
            UUID authenticatedUserId,
            UUID previousAppointmentId,
            CreateFollowUpAppointmentRequest request
    ) {
        Doctor doctor = findDoctor(authenticatedUserId);
        Appointment previousAppointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(previousAppointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
        requireFollowUpEligible(previousAppointment);

        Instant scheduledStartAt = request.scheduledStartAt().toInstant();
        DoctorService doctorService = slotService.requireAvailableSlot(
                doctor.getId(),
                request.serviceId(),
                scheduledStartAt
        );

        Appointment followUp = new Appointment();
        followUp.setPatient(previousAppointment.getPatient());
        followUp.setDoctorService(doctorService);
        followUp.setPreviousAppointment(previousAppointment);
        followUp.setScheduledStartAt(scheduledStartAt);
        followUp.setStatus(AppointmentStatus.BOOKED);

        try {
            appointmentRepository.saveAndFlush(followUp);
        } catch (DataIntegrityViolationException exception) {
            if (PostgreSqlErrorUtils.isUniqueViolation(exception)) {
                throw slotConflict();
            }
            throw exception;
        }

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(followUp);
        history.setAction(AppointmentAction.BOOK);
        history.setChangedBy(doctor.getUser());
        history.setDetails(bookDetails(followUp));
        appointmentHistoryRepository.saveAndFlush(history);

        return toResponse(followUp);
    }

    private Doctor findDoctor(UUID authenticatedUserId) {
        return doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
    }

    private void requireFollowUpEligible(Appointment previousAppointment) {
        if (previousAppointment.getStatus() != AppointmentStatus.IN_PROGRESS
                && previousAppointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new ConflictException(
                    "Chỉ có thể đặt tái khám cho lịch hẹn đang khám hoặc đã hoàn tất"
            );
        }

        MedicalRecord medicalRecord = medicalRecordRepository
                .findByAppointmentId(previousAppointment.getId())
                .orElseThrow(() -> new ConflictException(
                        "Lịch hẹn chưa có bệnh án yêu cầu tái khám"
                ));
        if (!medicalRecord.isFollowUpRequired()) {
            throw new ConflictException("Bệnh án không yêu cầu tái khám");
        }
    }

    private ObjectNode bookDetails(Appointment followUp) {
        OffsetDateTime scheduledAt = toClinicOffset(followUp.getScheduledStartAt());
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("status", AppointmentStatus.BOOKED.name());
        details.put("booking_type", "FOLLOW_UP");
        details.put("previous_appointment_id", followUp.getPreviousAppointment().getId().toString());
        details.put("doctor_id", followUp.getDoctorService().getDoctor().getId().toString());
        details.put("service_id", followUp.getDoctorService().getService().getId().toString());
        details.put("scheduled_start_at", scheduledAt.toString());
        return details;
    }

    private FollowUpAppointmentResponse toResponse(Appointment followUp) {
        OffsetDateTime scheduledStart = toClinicOffset(followUp.getScheduledStartAt());
        return new FollowUpAppointmentResponse(
                followUp.getId(),
                followUp.getPreviousAppointment().getId(),
                followUp.getPatient().getId(),
                followUp.getDoctorService().getDoctor().getId(),
                followUp.getDoctorService().getService().getId(),
                scheduledStart,
                scheduledStart.plus(slotDuration),
                followUp.getStatus(),
                followUp.getCreatedAt()
        );
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private ConflictException slotConflict() {
        return new ConflictException(
                "Khung giờ vừa được người khác đặt. Vui lòng chọn khung giờ khác"
        );
    }
}
