package com.dentalclinic.medicalrecord.entity;

import com.dentalclinic.appointment.entity.Appointment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "medical_records")
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "diagnosis", nullable = false, columnDefinition = "text")
    private String diagnosis;

    @Column(name = "treatment", nullable = false, columnDefinition = "text")
    private String treatment;

    @Column(name = "treatment_plan", columnDefinition = "text")
    private String treatmentPlan;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "follow_up_required", nullable = false)
    private boolean followUpRequired;

    @Column(name = "recommended_follow_up_date")
    private LocalDate recommendedFollowUpDate;

    @Column(name = "follow_up_note", columnDefinition = "text")
    private String followUpNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "prescription", nullable = false, columnDefinition = "jsonb")
    private JsonNode prescription = JsonNodeFactory.instance.arrayNode();

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
