package com.dentalclinic.appointment.service;

import com.dentalclinic.appointment.dto.CompleteExaminationResponse;
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
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Service
public class AppointmentCompleteExaminationService {

    private static final String INVALID_MEDICAL_RECORD_MESSAGE =
            "Phải có bệnh án hợp lệ trước khi hoàn tất khám";

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final AppointmentHistoryRepository appointmentHistoryRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ClinicTimeProvider clinicTimeProvider;

    public AppointmentCompleteExaminationService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            AppointmentHistoryRepository appointmentHistoryRepository,
            MedicalRecordRepository medicalRecordRepository,
            ClinicTimeProvider clinicTimeProvider
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.appointmentHistoryRepository = appointmentHistoryRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.clinicTimeProvider = clinicTimeProvider;
    }

    @Transactional
    public CompleteExaminationResponse complete(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        Appointment appointment = appointmentRepository
                .findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));

        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Chỉ có thể hoàn tất lịch hẹn đang ở trạng thái IN_PROGRESS"
            );
        }
        if (appointment.getActualStartAt() == null) {
            throw new ConflictException("Lịch hẹn chưa có thời gian bắt đầu khám");
        }

        MedicalRecord medicalRecord = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new ConflictException(INVALID_MEDICAL_RECORD_MESSAGE));
        validateMedicalRecord(medicalRecord);

        Instant actualEndAt = clinicTimeProvider.now();
        if (actualEndAt.isBefore(appointment.getActualStartAt())) {
            throw new ConflictException("Thời gian hoàn tất khám không hợp lệ");
        }

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointment.setActualEndAt(actualEndAt);
        appointmentRepository.saveAndFlush(appointment);

        AppointmentHistory history = new AppointmentHistory();
        history.setAppointment(appointment);
        history.setAction(AppointmentAction.COMPLETE);
        history.setChangedBy(doctor.getUser());
        history.setDetails(completeDetails(appointment, medicalRecord, actualEndAt));
        appointmentHistoryRepository.saveAndFlush(history);

        return new CompleteExaminationResponse(
                appointment.getId(),
                appointment.getStatus(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffsetOrNull(appointment.getCheckedInAt()),
                toClinicOffset(appointment.getActualStartAt()),
                toClinicOffset(actualEndAt)
        );
    }

    private void validateMedicalRecord(MedicalRecord medicalRecord) {
        boolean invalidRequiredFields = !hasText(medicalRecord.getDiagnosis())
                || !hasText(medicalRecord.getTreatment());
        boolean invalidPrescription = medicalRecord.getPrescription() == null
                || !medicalRecord.getPrescription().isArray();
        boolean hasFollowUpDate = medicalRecord.getRecommendedFollowUpDate() != null;
        boolean hasFollowUpNote = hasText(medicalRecord.getFollowUpNote());
        boolean invalidFollowUp = medicalRecord.isFollowUpRequired()
                ? !hasFollowUpDate && !hasFollowUpNote
                : hasFollowUpDate || hasFollowUpNote;

        if (invalidRequiredFields || invalidPrescription || invalidFollowUp) {
            throw new ConflictException(INVALID_MEDICAL_RECORD_MESSAGE);
        }
    }

    private ObjectNode completeDetails(
            Appointment appointment,
            MedicalRecord medicalRecord,
            Instant actualEndAt
    ) {
        ObjectNode details = JsonNodeFactory.instance.objectNode();
        details.put("old_status", AppointmentStatus.IN_PROGRESS.name());
        details.put("new_status", AppointmentStatus.COMPLETED.name());
        details.put("scheduled_start_at", toClinicOffset(appointment.getScheduledStartAt()).toString());
        putNullableTime(details, "checked_in_at", appointment.getCheckedInAt());
        details.put("actual_start_at", toClinicOffset(appointment.getActualStartAt()).toString());
        details.put("actual_end_at", toClinicOffset(actualEndAt).toString());
        details.put("medical_record_id", medicalRecord.getId().toString());
        details.put("follow_up_required", medicalRecord.isFollowUpRequired());
        return details;
    }

    private void putNullableTime(ObjectNode details, String field, Instant instant) {
        if (instant == null) {
            details.putNull(field);
        } else {
            details.put(field, toClinicOffset(instant).toString());
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }

    private OffsetDateTime toClinicOffsetOrNull(Instant instant) {
        return instant == null ? null : toClinicOffset(instant);
    }
}
