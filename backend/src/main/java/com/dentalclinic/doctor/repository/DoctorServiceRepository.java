package com.dentalclinic.doctor.repository;

import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DoctorServiceRepository extends JpaRepository<DoctorService, DoctorServiceId> {

    @Query("""
            select doctorService
            from DoctorService doctorService
            join fetch doctorService.service dentalService
            where doctorService.doctor.id = :doctorId
            order by dentalService.name asc, dentalService.id asc
            """)
    List<DoctorService> findAllWithServiceByDoctorId(@Param("doctorId") UUID doctorId);

    @Query("""
            select doctorService
            from DoctorService doctorService
            join fetch doctorService.doctor doctor
            join fetch doctor.user doctorUser
            where doctorService.service.id = :serviceId
              and doctorUser.status = :accountStatus
            order by doctorUser.fullName asc, doctor.id asc
            """)
    List<DoctorService> findAllWithDoctorByServiceIdAndAccountStatus(
            @Param("serviceId") UUID serviceId,
            @Param("accountStatus") UserStatus accountStatus
    );
}
