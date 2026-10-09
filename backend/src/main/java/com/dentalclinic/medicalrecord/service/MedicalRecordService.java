package com.dentalclinic.medicalrecord.service;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.appointment.entity.AppointmentStatus;
import com.dentalclinic.appointment.repository.AppointmentRepository;
import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.medicalrecord.dto.MedicalRecordRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.repository.MedicalRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class MedicalRecordService {

    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public MedicalRecordService(
            DoctorRepository doctorRepository,
            AppointmentRepository appointmentRepository,
            MedicalRecordRepository medicalRecordRepository
    ) {
        this.doctorRepository = doctorRepository;
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    @Transactional
    public MedicalRecordResponse create(
            UUID authenticatedUserId,
            UUID appointmentId,
            MedicalRecordRequest request
    ) {
        Appointment appointment = findOwnedAppointmentForUpdate(
                authenticatedUserId,
                appointmentId
        );
        requireInProgress(appointment);
        validateFollowUp(request);
        if (medicalRecordRepository.existsByAppointmentId(appointmentId)) {
            throw new ConflictException("Lịch hẹn đã có bệnh án");
        }

        MedicalRecord medicalRecord = new MedicalRecord();
        medicalRecord.setAppointment(appointment);
        applyRequest(medicalRecord, request);
        return toResponse(medicalRecordRepository.saveAndFlush(medicalRecord));
    }

    @Transactional
    public MedicalRecordResponse update(
            UUID authenticatedUserId,
            UUID appointmentId,
            MedicalRecordRequest request
    ) {
        Appointment appointment = findOwnedAppointmentForUpdate(
                authenticatedUserId,
                appointmentId
        );
        requireInProgress(appointment);
        validateFollowUp(request);

        MedicalRecord medicalRecord = medicalRecordRepository.findByAppointmentId(appointmentId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bệnh án"));
        applyRequest(medicalRecord, request);
        return toResponse(medicalRecordRepository.saveAndFlush(medicalRecord));
    }

    private Appointment findOwnedAppointmentForUpdate(
            UUID authenticatedUserId,
            UUID appointmentId
    ) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        return appointmentRepository.findForUpdateByIdAndDoctorId(appointmentId, doctor.getId())
                .orElseThrow(() -> new NotFoundException("Không tìm thấy lịch hẹn"));
    }

    private void requireInProgress(Appointment appointment) {
        if (appointment.getStatus() != AppointmentStatus.IN_PROGRESS) {
            throw new ConflictException(
                    "Chỉ có thể nhập bệnh án cho lịch hẹn đang ở trạng thái IN_PROGRESS"
            );
        }
    }

    private void validateFollowUp(MedicalRecordRequest request) {
        if (request.followUpRequired() == null) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Phải chọn có cần tái khám hay không"
            );
        }
        boolean followUpRequired = Boolean.TRUE.equals(request.followUpRequired());
        boolean hasFollowUpDate = request.recommendedFollowUpDate() != null;
        boolean hasFollowUpNote = hasText(request.followUpNote());
        if (followUpRequired && !hasFollowUpDate && !hasFollowUpNote) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Khi cần tái khám phải có ngày đề xuất hoặc ghi chú tái khám"
            );
        }
        if (!followUpRequired && (hasFollowUpDate || hasFollowUpNote)) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Không được nhập ngày hoặc ghi chú tái khám khi không cần tái khám"
            );
        }
    }

    private void applyRequest(MedicalRecord medicalRecord, MedicalRecordRequest request) {
        medicalRecord.setDiagnosis(request.diagnosis().trim());
        medicalRecord.setTreatment(request.treatment().trim());
        medicalRecord.setTreatmentPlan(normalizeOptional(request.treatmentPlan()));
        medicalRecord.setNotes(normalizeOptional(request.notes()));
        medicalRecord.setPrescription(toPrescriptionJson(request.prescription()));
        medicalRecord.setFollowUpRequired(Boolean.TRUE.equals(request.followUpRequired()));
        medicalRecord.setRecommendedFollowUpDate(request.recommendedFollowUpDate());
        medicalRecord.setFollowUpNote(normalizeOptional(request.followUpNote()));
    }

    private ArrayNode toPrescriptionJson(List<PrescriptionItem> prescription) {
        ArrayNode result = JsonNodeFactory.instance.arrayNode();
        if (prescription == null) {
            return result;
        }
        prescription.forEach(item -> result.addObject()
                .put("medicationName", item.medicationName().trim())
                .put("dosage", item.dosage().trim())
                .put("quantity", item.quantity())
                .put("instructions", item.instructions().trim()));
        return result;
    }

    private MedicalRecordResponse toResponse(MedicalRecord medicalRecord) {
        return new MedicalRecordResponse(
                medicalRecord.getId(),
                medicalRecord.getAppointment().getId(),
                medicalRecord.getDiagnosis(),
                medicalRecord.getTreatment(),
                medicalRecord.getTreatmentPlan(),
                medicalRecord.getNotes(),
                toPrescriptionItems(medicalRecord.getPrescription()),
                medicalRecord.isFollowUpRequired(),
                medicalRecord.getRecommendedFollowUpDate(),
                medicalRecord.getFollowUpNote(),
                medicalRecord.getCreatedAt(),
                medicalRecord.getUpdatedAt()
        );
    }

    private List<PrescriptionItem> toPrescriptionItems(JsonNode prescription) {
        if (prescription == null || !prescription.isArray()) {
            return List.of();
        }
        List<PrescriptionItem> items = new ArrayList<>();
        prescription.forEach(item -> items.add(new PrescriptionItem(
                item.path("medicationName").asText(),
                item.path("dosage").asText(),
                item.path("quantity").isInt() ? item.path("quantity").intValue() : null,
                item.path("instructions").asText()
        )));
        return List.copyOf(items);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
