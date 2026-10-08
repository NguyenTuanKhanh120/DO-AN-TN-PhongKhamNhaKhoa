package com.dentalclinic.doctor.repository;

import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorServiceRepository extends JpaRepository<DoctorService, DoctorServiceId> {
}
