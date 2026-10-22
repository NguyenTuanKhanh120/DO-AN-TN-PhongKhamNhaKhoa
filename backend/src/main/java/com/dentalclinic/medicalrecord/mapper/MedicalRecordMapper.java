package com.dentalclinic.medicalrecord.mapper;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class MedicalRecordMapper {

    private final ClinicTimeProvider clinicTimeProvider;

    public MedicalRecordMapper(ClinicTimeProvider clinicTimeProvider) {
        this.clinicTimeProvider = clinicTimeProvider;
    }

    public MedicalRecordResponse toResponse(MedicalRecord medicalRecord) {
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

    public PatientMedicalRecordResponse toPatientResponse(MedicalRecord medicalRecord) {
        Appointment appointment = medicalRecord.getAppointment();
        DoctorService doctorService = appointment.getDoctorService();
        Doctor doctor = doctorService.getDoctor();
        return new PatientMedicalRecordResponse(
                medicalRecord.getId(),
                appointment.getId(),
                doctor.getId(),
                doctor.getUser().getFullName(),
                doctor.getSpecialization(),
                doctorService.getService().getId(),
                doctorService.getService().getName(),
                toClinicOffset(appointment.getScheduledStartAt()),
                toClinicOffset(appointment.getActualStartAt()),
                toClinicOffset(appointment.getActualEndAt()),
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

    public ArrayNode toPrescriptionJson(List<PrescriptionItem> prescription) {
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

    public List<PrescriptionItem> toPrescriptionItems(JsonNode prescription) {
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

    private OffsetDateTime toClinicOffset(Instant instant) {
        return instant == null
                ? null
                : clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }
}
