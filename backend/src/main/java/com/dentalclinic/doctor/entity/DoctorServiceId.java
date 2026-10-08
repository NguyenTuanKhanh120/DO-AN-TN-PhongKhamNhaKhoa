package com.dentalclinic.doctor.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class DoctorServiceId implements Serializable {

    @Column(name = "doctor_id", nullable = false)
    private UUID doctorId;

    @Column(name = "service_id", nullable = false)
    private UUID serviceId;
}
