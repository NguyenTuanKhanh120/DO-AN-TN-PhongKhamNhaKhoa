package com.dentalclinic.doctor.repository;

import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface DoctorRepository extends JpaRepository<Doctor, UUID>, JpaSpecificationExecutor<Doctor> {

    @Override
    @EntityGraph(attributePaths = "user")
    Page<Doctor> findAll(Specification<Doctor> specification, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "user")
    Optional<Doctor> findById(UUID id);

    @EntityGraph(attributePaths = "user")
    Optional<Doctor> findByIdAndUser_Status(UUID id, UserStatus userStatus);

    @EntityGraph(attributePaths = "user")
    Optional<Doctor> findByUserId(UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select doctor
            from Doctor doctor
            where doctor.user.id = :userId
            """)
    Optional<Doctor> findForUpdateByUserId(@Param("userId") UUID userId);

    boolean existsByLicenseNumber(String licenseNumber);

    boolean existsByLicenseNumberAndIdNot(String licenseNumber, UUID id);
}
