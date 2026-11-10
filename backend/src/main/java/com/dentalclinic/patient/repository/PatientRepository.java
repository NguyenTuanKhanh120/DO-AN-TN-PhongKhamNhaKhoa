package com.dentalclinic.patient.repository;

import com.dentalclinic.patient.entity.Patient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface PatientRepository extends JpaRepository<Patient, UUID>,
        JpaSpecificationExecutor<Patient> {

    @EntityGraph(attributePaths = "user")
    Optional<Patient> findByUserId(UUID userId);

    boolean existsByUserId(UUID userId);

    boolean existsByFullNameIgnoreCaseAndPhone(String fullName, String phone);

    boolean existsByFullNameIgnoreCaseAndPhoneAndDateOfBirth(
            String fullName,
            String phone,
            LocalDate dateOfBirth
    );
}
