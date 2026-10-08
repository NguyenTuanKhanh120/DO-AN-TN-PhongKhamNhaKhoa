package com.dentalclinic.appointment.entity;

import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.patient.entity.Patient;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinColumns;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.generator.EventType;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumns({
            @JoinColumn(name = "doctor_id", referencedColumnName = "doctor_id", nullable = false),
            @JoinColumn(name = "service_id", referencedColumnName = "service_id", nullable = false)
    })
    private DoctorService doctorService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_appointment_id")
    private Appointment previousAppointment;

    @Column(name = "scheduled_start_at", nullable = false)
    private Instant scheduledStartAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "appointment_status_enum")
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    @Column(name = "checked_in_at")
    private Instant checkedInAt;

    @Column(name = "actual_start_at")
    private Instant actualStartAt;

    @Column(name = "actual_end_at")
    private Instant actualEndAt;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
