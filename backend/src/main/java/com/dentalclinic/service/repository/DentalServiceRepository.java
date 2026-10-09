package com.dentalclinic.service.repository;

import com.dentalclinic.service.entity.DentalService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface DentalServiceRepository
        extends JpaRepository<DentalService, UUID>, JpaSpecificationExecutor<DentalService> {

    boolean existsByName(String name);

    boolean existsByNameAndIdNot(String name, UUID id);
}
