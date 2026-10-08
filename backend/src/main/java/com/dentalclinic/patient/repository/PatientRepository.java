package com.dentalclinic.patient.repository;

import com.dentalclinic.patient.entity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);
}
