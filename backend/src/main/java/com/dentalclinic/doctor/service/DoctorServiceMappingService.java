package com.dentalclinic.doctor.service;

import com.dentalclinic.common.constant.ErrorCode;
import com.dentalclinic.common.exception.BusinessException;
import com.dentalclinic.common.exception.ConflictException;
import com.dentalclinic.common.exception.NotFoundException;
import com.dentalclinic.doctor.dto.DoctorAssignedServiceResponse;
import com.dentalclinic.doctor.dto.DoctorResponse;
import com.dentalclinic.doctor.entity.Doctor;
import com.dentalclinic.doctor.entity.DoctorService;
import com.dentalclinic.doctor.entity.DoctorServiceId;
import com.dentalclinic.doctor.repository.DoctorRepository;
import com.dentalclinic.doctor.repository.DoctorServiceRepository;
import com.dentalclinic.service.dto.DentalServiceResponse;
import com.dentalclinic.service.entity.DentalService;
import com.dentalclinic.service.repository.DentalServiceRepository;
import com.dentalclinic.user.entity.User;
import com.dentalclinic.user.entity.UserStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DoctorServiceMappingService {

    private final DoctorRepository doctorRepository;
    private final DentalServiceRepository dentalServiceRepository;
    private final DoctorServiceRepository doctorServiceRepository;
    private final long slotDurationMinutes;

    public DoctorServiceMappingService(
            DoctorRepository doctorRepository,
            DentalServiceRepository dentalServiceRepository,
            DoctorServiceRepository doctorServiceRepository,
            @Value("${clinic.appointment.slot-duration-minutes:60}") long slotDurationMinutes
    ) {
        if (slotDurationMinutes <= 0) {
            throw new IllegalArgumentException("Cấu hình thời lượng slot không hợp lệ");
        }
        this.doctorRepository = doctorRepository;
        this.dentalServiceRepository = dentalServiceRepository;
        this.doctorServiceRepository = doctorServiceRepository;
        this.slotDurationMinutes = slotDurationMinutes;
    }

    @Transactional(readOnly = true)
    public List<DentalServiceResponse> getServicesByDoctor(UUID doctorId) {
        doctorRepository.findByIdAndUser_Status(doctorId, UserStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        return doctorServiceRepository.findAllWithServiceByDoctorId(doctorId).stream()
                .map(DoctorService::getService)
                .map(this::toServiceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> getDoctorsByService(UUID serviceId) {
        dentalServiceRepository.findById(serviceId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy dịch vụ nha khoa"));
        return doctorServiceRepository.findAllWithDoctorByServiceIdAndAccountStatus(
                        serviceId,
                        UserStatus.ACTIVE
                ).stream()
                .map(DoctorService::getDoctor)
                .map(this::toDoctorResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DoctorAssignedServiceResponse> getMyActiveServices(UUID authenticatedUserId) {
        Doctor doctor = doctorRepository.findByUserId(authenticatedUserId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy hồ sơ bác sĩ"));
        return doctorServiceRepository.findAllWithActiveServiceByDoctorId(doctor.getId()).stream()
                .map(DoctorService::getService)
                .map(this::toAssignedServiceResponse)
                .toList();
    }

    @Transactional
    public List<DentalServiceResponse> replaceDoctorServices(UUID doctorId, List<UUID> serviceIds) {
        Doctor doctor = doctorRepository.findById(doctorId)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy bác sĩ"));
        LinkedHashSet<UUID> requestedIds = validateAndDeduplicate(serviceIds);
        List<DentalService> requestedServices = dentalServiceRepository.findAllById(requestedIds);
        if (requestedServices.size() != requestedIds.size()) {
            throw new NotFoundException("Không tìm thấy một hoặc nhiều dịch vụ nha khoa");
        }

        List<DoctorService> currentMappings = doctorServiceRepository.findAllWithServiceByDoctorId(doctorId);
        Set<UUID> currentServiceIds = currentMappings.stream()
                .map(mapping -> mapping.getId().getServiceId())
                .collect(Collectors.toSet());
        List<DoctorService> mappingsToRemove = currentMappings.stream()
                .filter(mapping -> !requestedIds.contains(mapping.getId().getServiceId()))
                .toList();
        List<DoctorService> mappingsToAdd = requestedServices.stream()
                .filter(dentalService -> !currentServiceIds.contains(dentalService.getId()))
                .map(dentalService -> createMapping(doctor, dentalService))
                .toList();

        try {
            if (!mappingsToRemove.isEmpty()) {
                doctorServiceRepository.deleteAll(mappingsToRemove);
                doctorServiceRepository.flush();
            }
            if (!mappingsToAdd.isEmpty()) {
                doctorServiceRepository.saveAllAndFlush(mappingsToAdd);
            }
        } catch (DataIntegrityViolationException exception) {
            throw new ConflictException("Không thể cập nhật mapping bác sĩ - dịch vụ do dữ liệu đang được sử dụng");
        }

        return doctorServiceRepository.findAllWithServiceByDoctorId(doctorId).stream()
                .map(DoctorService::getService)
                .map(this::toServiceResponse)
                .toList();
    }

    private LinkedHashSet<UUID> validateAndDeduplicate(List<UUID> serviceIds) {
        LinkedHashSet<UUID> distinctIds = new LinkedHashSet<>(serviceIds);
        if (distinctIds.size() != serviceIds.size()) {
            throw new BusinessException(
                    ErrorCode.INVALID_REQUEST,
                    "Danh sách dịch vụ không được chứa ID trùng lặp"
            );
        }
        return distinctIds;
    }

    private DoctorService createMapping(Doctor doctor, DentalService dentalService) {
        DoctorService mapping = new DoctorService();
        mapping.setId(new DoctorServiceId(doctor.getId(), dentalService.getId()));
        mapping.setDoctor(doctor);
        mapping.setService(dentalService);
        return mapping;
    }

    private DentalServiceResponse toServiceResponse(DentalService dentalService) {
        return new DentalServiceResponse(
                dentalService.getId(),
                dentalService.getName(),
                dentalService.getDescription(),
                dentalService.getPrice(),
                dentalService.getImageUrl(),
                dentalService.isActive(),
                dentalService.getCreatedAt(),
                dentalService.getUpdatedAt()
        );
    }

    private DoctorAssignedServiceResponse toAssignedServiceResponse(DentalService dentalService) {
        return new DoctorAssignedServiceResponse(
                dentalService.getId(),
                dentalService.getName(),
                dentalService.getDescription(),
                dentalService.getPrice(),
                slotDurationMinutes,
                dentalService.getImageUrl(),
                dentalService.isActive()
        );
    }

    private DoctorResponse toDoctorResponse(Doctor doctor) {
        User user = doctor.getUser();
        return new DoctorResponse(
                doctor.getId(),
                user.getFullName(),
                doctor.getSpecialization(),
                doctor.getLicenseNumber(),
                doctor.getBio(),
                doctor.getRating(),
                doctor.getExperienceYears(),
                doctor.getWorkingStatus(),
                doctor.getCreatedAt(),
                doctor.getUpdatedAt()
        );
    }
}
