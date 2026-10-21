package com.dentalclinic.medicalrecord.mapper;

import com.dentalclinic.appointment.entity.Appointment;
import com.dentalclinic.common.util.ClinicTimeProvider;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.medicalrecord.dto.MedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.MedicalRecordDraftPrescriptionItem;
import com.dentalclinic.medicalrecord.dto.MedicalRecordDraftRequest;
import com.dentalclinic.medicalrecord.dto.MedicalRecordDraftResponse;
import com.dentalclinic.medicalrecord.dto.PatientMedicalRecordResponse;
import com.dentalclinic.medicalrecord.dto.PrescriptionItem;
import com.dentalclinic.medicalrecord.entity.MedicalRecord;
import com.dentalclinic.medicalrecord.entity.MedicalRecordDraft;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.LocalDate;
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

    public JsonNode toDraftJson(MedicalRecordDraftRequest request) {
        var draft = JsonNodeFactory.instance.objectNode();
        putNullable(draft, "diagnosis", request.diagnosis());
        putNullable(draft, "treatment", request.treatment());
        putNullable(draft, "treatmentPlan", request.treatmentPlan());
        putNullable(draft, "notes", request.notes());

        ArrayNode prescription = draft.putArray("prescription");
        if (request.prescription() != null) {
            request.prescription().forEach(item -> {
                var itemNode = prescription.addObject();
                putNullable(itemNode, "medicationName", item.medicationName());
                putNullable(itemNode, "dosage", item.dosage());
                if (item.quantity() == null) {
                    itemNode.putNull("quantity");
                } else {
                    itemNode.put("quantity", item.quantity());
                }
                putNullable(itemNode, "instructions", item.instructions());
            });
        }

        if (request.followUpRequired() == null) {
            draft.putNull("followUpRequired");
        } else {
            draft.put("followUpRequired", request.followUpRequired());
        }
        putNullable(
                draft,
                "recommendedFollowUpDate",
                request.recommendedFollowUpDate() == null
                        ? null
                        : request.recommendedFollowUpDate().toString()
        );
        putNullable(draft, "followUpNote", request.followUpNote());
        return draft;
    }

    public MedicalRecordDraftResponse toDraftResponse(MedicalRecordDraft draft) {
        JsonNode data = draft.getDraftData();
        List<MedicalRecordDraftPrescriptionItem> prescription = new ArrayList<>();
        JsonNode items = data.path("prescription");
        if (items.isArray()) {
            items.forEach(item -> prescription.add(new MedicalRecordDraftPrescriptionItem(
                    nullableText(item.get("medicationName")),
                    nullableText(item.get("dosage")),
                    item.path("quantity").canConvertToInt() ? item.path("quantity").intValue() : null,
                    nullableText(item.get("instructions"))
            )));
        }
        String recommendedDate = nullableText(data.get("recommendedFollowUpDate"));
        return new MedicalRecordDraftResponse(
                draft.getId(),
                draft.getAppointment().getId(),
                nullableText(data.get("diagnosis")),
                nullableText(data.get("treatment")),
                nullableText(data.get("treatmentPlan")),
                nullableText(data.get("notes")),
                List.copyOf(prescription),
                data.path("followUpRequired").isBoolean()
                        ? data.path("followUpRequired").booleanValue()
                        : null,
                recommendedDate == null ? null : LocalDate.parse(recommendedDate),
                nullableText(data.get("followUpNote")),
                draft.getCreatedAt(),
                draft.getUpdatedAt()
        );
    }

    private void putNullable(
            com.fasterxml.jackson.databind.node.ObjectNode node,
            String field,
            String value
    ) {
        if (value == null) {
            node.putNull(field);
        } else {
            node.put(field, value);
        }
    }

    private String nullableText(JsonNode node) {
        return node == null || node.isNull() ? null : node.asText();
    }

    private OffsetDateTime toClinicOffset(Instant instant) {
        return instant == null
                ? null
                : clinicTimeProvider.toClinicTime(instant).toOffsetDateTime();
    }
}
